#!/usr/bin/env bash
# Cria a infraestrutura do Mercado Express na Azure (App Service + PostgreSQL).
# Rodar no Azure Cloud Shell (Bash), dentro da pasta do repositório:
#   bash azure/criar-recursos.sh <sufixo>        ex.: bash azure/criar-recursos.sh pfg2026
# O RabbitMQ fica no CloudAMQP (a Azure não oferece RabbitMQ gerenciado).
set -euo pipefail

SUFIXO="${1:?Uso: bash azure/criar-recursos.sh <sufixo>   (ex.: pfg2026 — só letras minúsculas e números)}"
LOC="${LOC:-canadacentral}"          # regiões liberadas na conta de estudante: brazilsouth, canadacentral, ...
RG="rg-mercado"
PLANO="plan-mercado"
APP="mercadoexpress-$SUFIXO"
BANCO="bancotranquilo-$SUFIXO"
PG="pg-mercado-$SUFIXO"
PG_USER="mercadoadmin"
DB="mercado_express"

echo "== Dados de configuração (nada disso vai para o GitHub) =="
read -rsp "Senha do PostgreSQL (8+ caracteres, com maiúscula, minúscula e número): " PG_PASS; echo
read -rp  "URL AMQP do CloudAMQP (amqps://...): " RABBIT_URL
read -rp  "URL do painel RabbitMQ Manager do CloudAMQP (https://...): " RABBIT_PAINEL
read -rp  "Gmail remetente: " MAIL_USER
read -rsp "Senha de app do Gmail: " MAIL_PASS; echo
read -rsp "Senha do usuário ADMIN (admin@mercadoexpress.com): " ADMIN_PASS; echo
read -rsp "Senha do usuário CLIENTE (cliente@mercadoexpress.com): " CLIENTE_PASS; echo
API_KEY="$(openssl rand -hex 24)"

echo "== 1/6 Grupo de recursos e plano do App Service (Linux B1) =="
az group create -n "$RG" -l "$LOC" -o none
az appservice plan create -g "$RG" -n "$PLANO" -l "$LOC" --is-linux --sku B1 -o none

echo "== 2/6 Web Apps Java 21 =="
az webapp create -g "$RG" -p "$PLANO" -n "$BANCO" --runtime "JAVA:21-java21" -o none
az webapp create -g "$RG" -p "$PLANO" -n "$APP"   --runtime "JAVA:21-java21" -o none
for W in "$BANCO" "$APP"; do
  az webapp config set -g "$RG" -n "$W" --always-on true -o none   # não "dorme"
  az webapp update -g "$RG" -n "$W" --https-only true -o none
  # libera o "publish profile" usado pelo GitHub Actions
  az resource update -g "$RG" --namespace Microsoft.Web --resource-type basicPublishingCredentialsPolicies \
    --parent "sites/$W" --name scm --set properties.allow=true -o none
done

echo "== 3/6 PostgreSQL Flexible Server (leva ~5-10 min) =="
az postgres flexible-server create -g "$RG" -n "$PG" -l "$LOC" \
  --tier Burstable --sku-name Standard_B1ms --storage-size 32 --version 16 \
  --admin-user "$PG_USER" --admin-password "$PG_PASS" \
  --public-access 0.0.0.0 --database-name "$DB" --yes -o none

echo "== 4/6 Configuração do Banco Tranquilo =="
az webapp config appsettings set -g "$RG" -n "$BANCO" -o none --settings \
  SERVER_PORT=80 BANCO_API_KEY="$API_KEY" JAVA_OPTS="-Xmx256m"

echo "== 5/6 Configuração do Mercado Express =="
az webapp config appsettings set -g "$RG" -n "$APP" -o none --settings \
  SERVER_PORT=80 JAVA_OPTS="-Xmx512m" \
  DB_URL="jdbc:postgresql://$PG.postgres.database.azure.com:5432/$DB?sslmode=require" \
  DB_USERNAME="$PG_USER" DB_PASSWORD="$PG_PASS" \
  SPRING_RABBITMQ_ADDRESSES="$RABBIT_URL" RABBITMQ_DASHBOARD_URL="$RABBIT_PAINEL" \
  BANCO_TRANQUILO_URL="https://$BANCO.azurewebsites.net" BANCO_API_KEY="$API_KEY" \
  MAIL_HOST=smtp.gmail.com MAIL_PORT=587 MAIL_USERNAME="$MAIL_USER" MAIL_PASSWORD="$MAIL_PASS" MAIL_FROM="$MAIL_USER" \
  ADMIN_PASSWORD="$ADMIN_PASS" CLIENTE_PASSWORD="$CLIENTE_PASS"

echo "== 6/6 Publish profiles para o GitHub Actions =="
az webapp deployment list-publishing-profiles -g "$RG" -n "$BANCO" --xml > "$HOME/publish-banco.xml"
az webapp deployment list-publishing-profiles -g "$RG" -n "$APP"   --xml > "$HOME/publish-mercado.xml"

cat <<FIM

Pronto! Agora no GitHub (Settings → Secrets and variables → Actions):
  Secrets:   AZURE_PUBLISH_PROFILE_BANCO   = conteúdo de: cat ~/publish-banco.xml
             AZURE_PUBLISH_PROFILE_MERCADO = conteúdo de: cat ~/publish-mercado.xml
  Variables: AZURE_WEBAPP_BANCO   = $BANCO
             AZURE_WEBAPP_MERCADO = $APP
Depois: aba Actions → "Deploy na Azure (App Service)" → Run workflow.

Aplicação:        https://$APP.azurewebsites.net
Banco Tranquilo:  https://$BANCO.azurewebsites.net/api/pagamentos/health
FIM
