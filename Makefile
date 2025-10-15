.PHONY: up plan apply package-lambda curl down test terraform-shell init

GRADLE ?= ./gradlew
TERRAFORM_DOCKER := docker compose run --rm terraform terraform

up:
	docker compose up -d
	@echo "Aguardando LocalStack ficar pronto..."
	@until curl -s http://localhost:4566/_localstack/health | grep -q '"services"'; do sleep 2; done
	@echo "LocalStack está pronto!"

init:
	$(TERRAFORM_DOCKER) -chdir=/workspace/terraform init

plan: init
	$(TERRAFORM_DOCKER) -chdir=/workspace/terraform plan

apply: package-lambda init
	$(TERRAFORM_DOCKER) -chdir=/workspace/terraform apply -auto-approve

package-lambda:
	@echo "Compilando Lambda..."
	$(GRADLE) :lambdas:payment-intake:shadowJar
	@echo "Criando diretório build/lambdas..."
	mkdir -p build/lambdas
	@echo "Copiando JAR..."
	cp lambdas/payment-intake/build/libs/payment-intake-all.jar build/lambdas/payment-intake.zip
	@echo "Lambda empacotada em build/lambdas/payment-intake.zip"
	@ls -lh build/lambdas/payment-intake.zip

curl:
	@which awslocal >/dev/null 2>&1 && API_ID=$$(awslocal apigateway get-rest-apis --query 'items[0].id' --output text) || API_ID=$$(aws --endpoint-url=http://localhost:4566 apigateway get-rest-apis --query 'items[0].id' --output text); \
	echo "Calling http://localhost:4566/restapis/$$API_ID/local/_user_request_/payments"; \
	curl -s -X POST "http://localhost:4566/restapis/$$API_ID/local/_user_request_/payments" \
	  -H 'Content-Type: application/json' \
	  -d '{"amount":"49.90","customerId":"C777"}' | sed -e 's/^/  /'

down:
	docker compose down -v

test:
	$(GRADLE) clean test

terraform-shell:
	docker compose run --rm terraform sh
