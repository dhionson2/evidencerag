# Publicação manual no GitHub

A publicação não foi executada. Revise o projeto e valide localmente antes de criar qualquer repositório público.

## Antes de criar o repositório remoto

1. Leia o relatório [`validation-report.md`](validation-report.md); configure Docker/pgvector e execute os testes de integração que ficaram pendentes.
2. Faça uma execução ponta a ponta com um provedor permitido. Não inclua documentos, logs, perguntas ou chaves reais na gravação.
3. Revise a licença MIT e gere/inspecione o relatório de licenças transitivas, por exemplo com CycloneDX ou outro verificador aprovado.
4. Confirme que `.env`, segredos, builds, arquivos de log e dados externos não estão no `git status`.
5. Decida o nome, a visibilidade e o dono do repositório remoto. Configure branch protection e alertas de segurança ao criá-lo.

## Comandos de publicação (PowerShell)

A partir da pasta extraída do ZIP, instale Git, JDK 21, Maven, Node 24 e Docker Desktop se desejar usar a stack local. Rode as validações indicadas no README; então:

```powershell
git init
git add .
git status --short
git diff --cached --check
git commit -m "feat: add EvidenceRAG document analysis copilot"
git branch -M main
```

Depois de criar o repositório vazio pela interface do GitHub e confirmar o URL exibido para você:

```powershell
git remote add origin <URL-DO-REPOSITORIO>
git push -u origin main
```

Substitua o placeholder pelo URL HTTPS ou SSH do repositório que você acabou de criar. Leia `git status` e o conteúdo staged antes do primeiro push. Nenhum comando de publicação foi executado durante a preparação deste projeto.
