#!/usr/bin/env bash
# Publica o Mercado Express na Azure com UM comando (rodar no Azure Cloud Shell, Bash):
#   cd CP5_MercadoExpress && bash azure/deploy.sh pjg2026
# Compila os dois serviços, cria App Service + PostgreSQL e publica. Pode ser rodado de novo sem problema.
set -euo pipefail
cd "$(dirname "$0")/.."

SUFIXO="${1:-}"
[[ "$SUFIXO" =~ ^[a-z0-9]{3,20}$ ]] || { echo "Uso: bash azure/deploy.sh <sufixo>  (só letras minúsculas e números, ex.: pjg2026)"; exit 1; }
LOC="${LOC:-canadacentral}"
RG="rg-mercado"; PLANO="plan-mercado"
APP="mercadoexpress-$SUFIXO"; BANCO="bancotranquilo-$SUFIXO"; PG="pg-mercado-$SUFIXO"
PG_USER="mercadoadmin"; DB="mercado_express"
CONF="$HOME/.mercado-deploy-$SUFIXO.env"

# ---------- dados (perguntados só na primeira vez; ficam salvos em $CONF) ----------
[ -f "$CONF" ] && source "$CONF"
[ -n "${RABBIT_URL:-}" ] || read -rp "1) URL AMQP do CloudAMQP (amqps://...): " RABBIT_URL
[ -n "${MAIL_USER:-}" ]  || read -rp "2) Gmail remetente: " MAIL_USER
[ -n "${MAIL_PASS:-}" ]  || { read -rsp "3) Senha de app do Gmail (não aparece ao digitar): " MAIL_PASS; echo; }
: "${PG_PASS:=Pg$(openssl rand -hex 10)A1}"
: "${API_KEY:=$(openssl rand -hex 24)}"
: "${ADMIN_PASS:=Admin-$(openssl rand -hex 4)}"
: "${CLIENTE_PASS:=Cliente-$(openssl rand -hex 4)}"
umask 077
{ for v in RABBIT_URL MAIL_USER MAIL_PASS PG_PASS API_KEY ADMIN_PASS CLIENTE_PASS; do printf '%s=%q\n' "$v" "${!v}"; done; } > "$CONF"
RABBIT_HOST="$(echo "$RABBIT_URL" | sed -E 's#^amqps?://[^@]*@([^/:]+).*#\1#')"

# ---------- Java 21 + Maven ----------
echo "== 1/5 Preparando Java 21 e Maven =="
if ! java -version 2>&1 | grep -q 'version "21'; then
  if [ ! -x "$HOME/jdk21/bin/java" ]; then
    mkdir -p "$HOME/jdk21"
    curl -fsSL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" | tar xz -C "$HOME/jdk21" --strip-components=1
  fi
  export JAVA_HOME="$HOME/jdk21"; export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v mvn >/dev/null; then
  if [ ! -x "$HOME/maven/bin/mvn" ]; then
    mkdir -p "$HOME/maven"
    curl -fsSL "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz" | tar xz -C "$HOME/maven" --strip-components=1
  fi
  export PATH="$HOME/maven/bin:$PATH"
fi

echo "== 2/5 Compilando (Maven) =="
(cd banco-tranquilo-api && mvn -q -B -DskipTests package)
(cd mercado-express && mvn -q -B -DskipTests package)
JAR_BANCO="$(ls banco-tranquilo-api/target/*.jar | head -1)"
JAR_APP="$(ls mercado-express/target/*.jar | head -1)"

echo "== 3/5 Criando App Service (2 Web Apps Java 21) =="
az group create -n "$RG" -l "$LOC" -o none
az appservice plan create -g "$RG" -n "$PLANO" -l "$LOC" --is-linux --sku B1 -o none
for W in "$BANCO" "$APP"; do
  az webapp create -g "$RG" -p "$PLANO" -n "$W" --runtime "JAVA:21-java21" -o none
  az webapp config set -g "$RG" -n "$W" --always-on true -o none
  az webapp update -g "$RG" -n "$W" --https-only true -o none
done

echo "== 4/5 PostgreSQL (na primeira vez leva ~5-10 min) =="
if ! az postgres flexible-server show -g "$RG" -n "$PG" -o none 2>/dev/null; then
  az postgres flexible-server create -g "$RG" -n "$PG" -l "$LOC" \
    --tier Burstable --sku-name Standard_B1ms --storage-size 32 --version 16 \
    --admin-user "$PG_USER" --admin-password "$PG_PASS" \
    --public-access 0.0.0.0 --database-name "$DB" --yes -o none
fi

az webapp config appsettings set -g "$RG" -n "$BANCO" -o none --settings \
  SERVER_PORT=80 BANCO_API_KEY="$API_KEY" JAVA_OPTS="-Xmx256m"
az webapp config appsettings set -g "$RG" -n "$APP" -o none --settings \
  SERVER_PORT=80 JAVA_OPTS="-Xmx512m" \
  DB_URL="jdbc:postgresql://$PG.postgres.database.azure.com:5432/$DB?sslmode=require" \
  DB_USERNAME="$PG_USER" DB_PASSWORD="$PG_PASS" \
  SPRING_RABBITMQ_ADDRESSES="$RABBIT_URL" RABBITMQ_DASHBOARD_URL="https://$RABBIT_HOST" \
  BANCO_TRANQUILO_URL="https://$BANCO.azurewebsites.net" BANCO_API_KEY="$API_KEY" \
  MAIL_HOST=smtp.gmail.com MAIL_PORT=587 MAIL_USERNAME="$MAIL_USER" MAIL_PASSWORD="$MAIL_PASS" MAIL_FROM="$MAIL_USER" \
  ADMIN_PASSWORD="$ADMIN_PASS" CLIENTE_PASSWORD="$CLIENTE_PASS"

echo "== 5/5 Publicando os .jar =="
az webapp deploy -g "$RG" -n "$BANCO" --src-path "$JAR_BANCO" --type jar -o none
az webapp deploy -g "$RG" -n "$APP"   --src-path "$JAR_APP"   --type jar -o none

cat <<FIM

==================== PRONTO ====================
Aplicação:        https://$APP.azurewebsites.net
Banco Tranquilo:  https://$BANCO.azurewebsites.net/api/pagamentos/health
Painel RabbitMQ:  https://$RABBIT_HOST
Login ADMIN:      admin@mercadoexpress.com   / $ADMIN_PASS
Login CLIENTE:    cliente@mercadoexpress.com / $CLIENTE_PASS
(o primeiro acesso pode levar 1-2 min enquanto a aplicação inicia)
Logs:             az webapp log tail -g $RG -n $APP
================================================
FIM
