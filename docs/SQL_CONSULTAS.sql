-- Consultas para evidenciar os registros no PostgreSQL
SELECT * FROM produtos ORDER BY id;
SELECT * FROM pedidos ORDER BY criado_em DESC;
SELECT * FROM itens_pedido ORDER BY pedido_id;
SELECT * FROM pagamentos ORDER BY atualizado_em DESC;
SELECT * FROM emails_enviados ORDER BY criado_em DESC;

-- Totais de pedidos por status
SELECT status, COUNT(*) AS quantidade FROM pedidos GROUP BY status;

-- Faturamento confirmado
SELECT COALESCE(SUM(total), 0) AS faturamento_confirmado
FROM pedidos WHERE status = 'CONFIRMADO';
