#!/usr/bin/env bash
set -euo pipefail

echo "🧹 Limpando projeto para publicação..."

# Parar containers
docker compose down -v

# Limpar builds
./gradlew clean
rm -rf .gradle/
rm -rf build/
rm -rf */build/
rm -rf */*/build/
rm -rf **/bin/

# Limpar LocalStack
rm -rf .localstack/

# Limpar Terraform
cd terraform/
rm -f terraform.tfstate*
rm -f .terraform.lock.hcl
rm -rf .terraform/
cd ..

# Limpar IDE
rm -rf .idea/
rm -rf .vscode/
find . -name "*.iml" -delete

echo "✅ Limpeza concluída!"
echo ""
echo "Próximos passos:"
echo "  git add ."
echo "  git commit -m 'Initial commit: Itaú Java/AWS Case'"
echo "  git push origin main"