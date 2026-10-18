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

-- Lucro bruto por produto (pedidos confirmados)
SELECT i.produto_nome,
       SUM(i.quantidade) AS unidades,
       SUM(i.preco_unitario * i.quantidade) AS faturamento,
       SUM((i.preco_unitario - i.custo_unitario) * i.quantidade) AS lucro_bruto
FROM itens_pedido i
JOIN pedidos p ON p.id = i.pedido_id
WHERE p.status = 'CONFIRMADO'
GROUP BY i.produto_nome
ORDER BY lucro_bruto DESC;

-- Controle de e-mails por status
SELECT status, COUNT(*) FROM emails_enviados GROUP BY status;

-- Produtos com estoque baixo
SELECT id, nome, estoque FROM produtos WHERE estoque <= 5 ORDER BY estoque;
