# Documentação Técnica - Itaú Java/AWS Case

## 📋 Sumário Executivo

Este projeto demonstra uma arquitetura de microserviços event-driven utilizando Java 21 e AWS (simulado via LocalStack), seguindo boas práticas de desenvolvimento e provisionamento de infraestrutura como código (IaC). O sistema implementa um fluxo completo de processamento de pagamentos, desde a ingestão via API Gateway até o armazenamento e processamento assíncrono.

---

## 🎯 Objetivo do Projeto

Demonstrar competências técnicas alinhadas aos requisitos da vaga:

- ✅ Desenvolvimento Java com **Spring Boot**
- ✅ Integração com serviços AWS (**Lambda**, **SQS**, **SNS**, **DynamoDB**, **API Gateway**)
- ✅ Provisionamento de infraestrutura com **Terraform**
- ✅ Arquitetura de microserviços e mensageria
- ✅ Containerização e ambiente local
- ✅ Migrations com Flyway

---

## 🏗️ Arquitetura do Sistema

### Visão Geral

```
                                   ┌─────────────────────────────────────┐
                                   │         Cliente HTTP                │
                                   └──────────────┬──────────────────────┘
                                                  │
                                                  │ POST /payments
                                                  ▼
                                   ┌─────────────────────────────────────┐
                                   │         API Gateway                 │
                                   │    (LocalStack Port 4566)           │
                                   └──────────────┬──────────────────────┘
                                                  │
                                                  │ Proxy Request
                                                  ▼
                                   ┌─────────────────────────────────────┐
                                   │    Lambda: payment-intake           │
                                   │    Handler: PaymentIntakeHandler    │
                                   │    - Valida payload                 │
                                   │    - Gera UUID                      │
                                   │    - Persiste no DynamoDB           │
                                   │    - Publica no SNS                 │
                                   └──────────┬────────────┬─────────────┘
                                              │            │
                                    ┌─────────▼──┐      ┌──▼─────────────┐
                                    │ DynamoDB   │      │   SNS Topic    │
                                    │  payments  │      │ payments-topic │
                                    └─────┬──────┘      └──┬─────────────┘
                                          │                 │
                                          │                 │ Fanout
                                          │                 ▼
                                          │         ┌───────────────────┐
                                          │         │    SQS Queue      │
                                          │         │ payments-queue    │
                                          │         └───────┬───────────┘
                                          │                 │
                                          │                 │ Polling (1s)
                                          │                 ▼
                                          │         ┌───────────────────┐
                      ┌───────────────────┤         │  Payment Worker   │
                      │ GET /payments/{id}│         │  (Spring Boot)    │
                      │                   │         │  - Consome SQS    │
                      ▼                   │         │  - Persiste no    │
           ┌─────────────────────┐        │         │    PostgreSQL     │
           │   Payment API       │        │         └────────┬──────────┘
           │   (Spring Boot)     │        │                  │
           │   Port 8081         │        │                  │ JDBC
           │   - Lê do DynamoDB  │◀───────┘                  ▼
           └─────────────────────┘                  ┌─────────────────┐
                                                    │   PostgreSQL    │
                                                    │ payment_events  │
                                                    │  (Audit Log)    │
                                                    └─────────────────┘
```

### Fluxo de Dados

1. **Ingestão**: Cliente envia `POST /payments` via API Gateway
2. **Processamento Síncrono**: Lambda valida e persiste no DynamoDB
3. **Publicação**: Lambda publica evento no SNS Topic (`payments-topic`)
4. **Distribuição**: SNS encaminha para SQS Queue (fanout pattern)
5. **Consumo Assíncrono**: Worker Spring Boot consome da fila SQS
6. **Auditoria**: Worker insere eventos em PostgreSQL (tabela `payment_events`)
7. **Consulta**: Payment API permite leitura via `GET /payments/{id}` **diretamente do DynamoDB**

> **⚠️ Nota Importante**: O Payment API **NÃO** lê do PostgreSQL. Ele consulta diretamente o DynamoDB para obter dados transacionais. O PostgreSQL é usado **exclusivamente pelo Worker** para auditoria/analytics

---

## 🛠️ Stack Tecnológica

### Core Technologies

| Tecnologia      | Versão   | Propósito                    |
| --------------- | -------- | ---------------------------- |
| **Java**        | 21 (LTS) | Linguagem principal          |
| **Gradle**      | 8.9      | Build automation             |
| **Spring Boot** | 3.3.3    | Framework para microserviços |
| **Spring JDBC** | 3.3.3    | Acesso a dados               |
| **Flyway**      | 10.11.0  | Migração de banco de dados   |

### AWS SDK & Services

| Serviço         | SDK Version     | Uso                            |
| --------------- | --------------- | ------------------------------ |
| **Lambda**      | Java 21 Runtime | Processamento serverless       |
| **API Gateway** | AWS SDK 2.20.56 | Exposição de APIs REST         |
| **DynamoDB**    | AWS SDK 2.20.56 | NoSQL para dados transacionais |
| **SNS**         | AWS SDK 2.25.39 | Pub/Sub messaging              |
| **SQS**         | AWS SDK 2.20.56 | Fila de mensagens              |
| **IAM**         | Terraform       | Controle de acesso             |

### Infraestrutura

| Componente         | Versão | Descrição                     |
| ------------------ | ------ | ----------------------------- |
| **Terraform**      | 1.7.5  | Infrastructure as Code        |
| **LocalStack**     | latest | Simulação AWS local           |
| **PostgreSQL**     | 16     | Banco relacional (simula RDS) |
| **Docker Compose** | v2+    | Orquestração de containers    |

### Ferramentas de Desenvolvimento

- **Jackson** 2.17.2 - Serialização JSON
- **JUnit Jupiter** 5.10.2 - Testes unitários
- **SLF4J + Logback** - Logging
- **AWS Lambda Java Core** 1.2.3 - Runtime Lambda
- **Shadow Plugin** 8.1.1 - Uber JARs para Lambda

---

## 📁 Estrutura do Projeto

```
itau-java-aws-case/
├── lambdas/
│   └── payment-intake/              # Lambda Function (Java 21)
│       ├── src/main/java/
│       │   └── com/itau/challenge/
│       │       └── PaymentIntakeHandler.java
│       └── build.gradle             # Shadow JAR config
│
├── services/
│   ├── payment-worker/              # Consumer Spring Boot
│   │   ├── src/main/java/
│   │   │   └── com/itau/challenge/worker/
│   │   │       ├── WorkerApp.java
│   │   │       ├── config/
│   │   │       │   └── SqsConfig.java
│   │   │       └── service/
│   │   │           └── PaymentConsumer.java
│   │   ├── src/main/resources/
│   │   │   ├── application.yml
│   │   │   └── db/migration/
│   │   │       └── V1__init.sql
│   │   └── build.gradle
│   │
│   └── payment-api/                 # REST API Spring Boot
│       ├── src/main/java/
│       │   └── com/itau/challenge/api/
│       │       ├── ApiApp.java
│       │       ├── config/
│       │       │   └── DdbConfig.java
│       │       └── web/
│       │           └── PaymentController.java
│       ├── src/main/resources/
│       │   └── application.yml
│       └── build.gradle
│
├── terraform/                       # IaC (Infrastructure as Code)
│   ├── main.tf                      # Provider AWS + LocalStack
│   ├── lambda.tf                    # Lambda + IAM Role
│   ├── apigw.tf                     # API Gateway + Integration
│   ├── dynamodb.tf                  # Tabela payments
│   ├── sns.tf                       # SNS Topic
│   └── sqs.tf                       # SQS Queue + Subscription
│
├── docker-compose.yml               # LocalStack + PostgreSQL
├── Makefile                         # Automação de comandos
├── build.gradle                     # Configuração raiz Gradle
├── settings.gradle                  # Multi-project setup
└── README.md                        # Documentação
```

---

## 🔧 Componentes Detalhados

### 1. Lambda Function - Payment Intake

**Arquivo**: `lambdas/payment-intake/src/main/java/com/itau/challenge/PaymentIntakeHandler.java`

**Responsabilidades**:

- Receber payload JSON via API Gateway
- Validar campos obrigatórios (`amount`, `customerId`)
- Gerar UUID para `paymentId`
- Persistir no DynamoDB (tabela `payments`)
- Publicar evento no SNS Topic (`payments-topic`)
- Retornar resposta HTTP 202 Accepted

**Tecnologias**:

- AWS Lambda Java 21 Runtime
- AWS SDK v2 (DynamoDB + SNS)
- Jackson para JSON

**Build**:

```bash
./gradlew :lambdas:payment-intake:shadowJar
```

Gera: `lambdas/payment-intake/build/libs/payment-intake-all.jar`

**Configuração Terraform**: `terraform/lambda.tf`

```hcl
resource "aws_lambda_function" "payment_intake" {
  function_name = "payment-intake"
  handler       = "com.itau.challenge.PaymentIntakeHandler::handleRequest"
  runtime       = "java21"
  timeout       = 15

  environment {
    variables = {
      TABLE_NAME      = aws_dynamodb_table.payments.name
      TOPIC_ARN       = aws_sns_topic.payments_topic.arn
      AWS_REGION      = "us-east-1"
      LOCALSTACK      = "true"
      LOCALSTACK_HOST = "host.docker.internal"
    }
  }
}
```

---

### 2. Payment Worker - Consumidor SQS

**Arquivo**: `services/payment-worker/src/main/java/com/itau/challenge/worker/service/PaymentConsumer.java`

**Responsabilidades**:

- Polling da fila SQS (`payments-queue`) a cada 1s
- Desserializar envelope SNS → payload do evento
- Validar campos do pagamento
- Inserir em PostgreSQL (tabela `payment_events`)
- Deletar mensagem da fila após sucesso

**Tecnologias**:

- Spring Boot 3.3.3
- Spring JDBC Template
- AWS SDK v2 (SQS)
- Flyway para migrações
- `@Scheduled` para polling

**Configuração**: `services/payment-worker/src/main/resources/application.yml`

```yaml
aws:
  region: us-east-1
  endpoint: http://localhost:4566
queues:
  payments: payments-queue
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payments
```

**Migração SQL**: `services/payment-worker/src/main/resources/db/migration/V1__init.sql`

```sql
create table payment_events (
  id bigserial primary key,
  payment_id varchar(64) not null,
  amount numeric(18,2) not null,
  customer_id varchar(64) not null,
  received_at timestamptz default now()
);
```

---

### 3. Payment API - Leitura de Pagamentos

**Arquivo**: `services/payment-api/src/main/java/com/itau/challenge/api/web/PaymentController.java`

**Endpoints**:

- `GET /health` - Health check
- `GET /payments/{id}` - Consulta pagamento por ID **diretamente no DynamoDB**

**Tecnologias**:

- Spring Boot 3.3.3
- Spring Web
- AWS SDK v2 (DynamoDB)
- Spring Actuator

> **⚠️ Nota Técnica**: Embora o `application.yml` contenha configuração de PostgreSQL, este serviço **não utiliza** banco relacional. A configuração está presente apenas para evitar erros de autoconfiguration do Spring Boot, mas nenhum DAO/Repository é injetado.

**Exemplo de Resposta**:

```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": "49.90",
  "customerId": "C777"
}
```

> **⚠️ Importante**: O campo `status` não está implementado. A resposta contém apenas `paymentId`, `amount` e `customerId`.

---

### 4. Infraestrutura Terraform

#### Provider Configuration

**Arquivo**: `terraform/main.tf`

```hcl
provider "aws" {
  access_key = "test"
  secret_key = "test"
  region     = "us-east-1"
  endpoints {
    apigateway = "http://localhost:4566"
    dynamodb   = "http://localhost:4566"
    lambda     = "http://localhost:4566"
    sns        = "http://localhost:4566"
    sqs        = "http://localhost:4566"
  }
}
```

#### Recursos Criados

| Recurso                              | Arquivo       | Descrição                           |
| ------------------------------------ | ------------- | ----------------------------------- |
| `aws_dynamodb_table.payments`        | `dynamodb.tf` | Tabela NoSQL, hash key: `paymentId` |
| `aws_sns_topic.payments_topic`       | `sns.tf`      | Tópico de eventos                   |
| `aws_sqs_queue.payments_queue`       | `sqs.tf`      | Fila de mensagens                   |
| `aws_sns_topic_subscription`         | `sqs.tf`      | Subscrição SNS→SQS                  |
| `aws_lambda_function.payment_intake` | `lambda.tf`   | Função serverless                   |
| `aws_api_gateway_rest_api.api`       | `apigw.tf`    | REST API                            |
| `aws_iam_role.lambda_role`           | `lambda.tf`   | Role para Lambda                    |

**Comandos**:

```bash
make init   # terraform init
make plan   # terraform plan
make apply  # terraform apply -auto-approve
```

---

## 🚀 Como Executar

### Pré-requisitos

```bash
# Verificar Java 21
java -version  # openjdk 21.x.x

# Verificar Docker
docker --version && docker compose version

# Instalar AWS CLI (opcional, para testes manuais)
pip install awscli-local
```

### Setup Completo

```bash
# 1. Subir infraestrutura local
docker compose up -d

# 2. Verificar Gradle Wrapper (se não existir, gerar)
./gradlew --version || gradle wrapper --gradle-version 8.9
chmod +x gradlew

# 3. Compilar Lambda
./gradlew :lambdas:payment-intake:shadowJar

# 4. Empacotar e provisionar infraestrutura
make package-lambda
make apply

# 5. Executar Worker (Terminal 1)
./gradlew :services:payment-worker:bootRun

# 6. Executar API (Terminal 2)
./gradlew :services:payment-api:bootRun
```

> **⚠️ Importante**: Os serviços Spring Boot (`payment-worker` e `payment-api`) devem rodar em **terminais separados** pois são processos bloqueantes.

### Testar o Fluxo

```bash
# Enviar pagamento via API Gateway
make curl

# Resposta esperada:
# {"paymentId":"550e8400-e29b-41d4-a716-446655440000"}

# Consultar no Payment API
curl http://localhost:8081/payments/{paymentId}

# Verificar no PostgreSQL
docker exec -it postgres psql -U postgres -d payments \
  -c "SELECT * FROM payment_events ORDER BY received_at DESC LIMIT 5;"
```

---

## 🧪 Testes

### Status Atual

⚠️ **Este projeto é um protótipo demonstrativo**. Testes unitários e de integração não estão implementados.

### Testes Manuais

```bash
# Testar fluxo completo end-to-end
make curl

# Verificar Worker logs
docker logs -f payment-worker 2>&1 | grep -i payment

# Consultar API
PAYMENT_ID="550e8400-e29b-41d4-a716-446655440000"
curl http://localhost:8081/payments/$PAYMENT_ID

# Verificar no PostgreSQL
docker exec -it postgres psql -U postgres -d payments \
  -c "SELECT * FROM payment_events ORDER BY received_at DESC LIMIT 5;"

# Verificar no DynamoDB (via awslocal)
awslocal dynamodb scan --table-name payments --max-items 5
```

### Melhorias Futuras

Para um ambiente de produção, seria necessário implementar:

- [ ] Testes unitários com JUnit 5 + Mockito
- [ ] Testes de integração com Testcontainers (LocalStack + PostgreSQL)
- [ ] Testes de contrato (Spring Cloud Contract)
- [ ] Testes de carga (JMeter / Gatling)
- [ ] Análise de cobertura (JaCoCo)

---

## 📊 Monitoramento e Logs

### LocalStack Logs

```bash
docker logs -f localstack
```

### Application Logs

```yaml
# application.yml
logging:
  level:
    root: INFO
    com.itau.challenge: DEBUG
    software.amazon.awssdk: WARN
```

### Health Checks

- Payment API: `http://localhost:8081/health`
- PostgreSQL: `docker exec postgres pg_isready`
- LocalStack: `curl http://localhost:4566/_localstack/health`

---

## 🔄 Migração para AWS Real

### Checklist

1. **Código Lambda**:
   - Remover variáveis `LOCALSTACK` e `LOCALSTACK_HOST` do handler
   - Configurar `endpointOverride` condicional via env vars
2. **Terraform**:

   ```hcl
   # Remover bloco endpoints {} do provider
   provider "aws" {
     region = "us-east-1"
     # Usar credenciais reais (AWS CLI / IAM Roles)
   }

   # Remover variáveis de ambiente do Lambda
   resource "aws_lambda_function" "payment_intake" {
     # ...
     environment {
       variables = {
         TABLE_NAME = aws_dynamodb_table.payments.name
         TOPIC_ARN  = aws_sns_topic.payments_topic.arn
         AWS_REGION = "us-east-1"
         # Remover LOCALSTACK e LOCALSTACK_HOST
       }
     }
   }
   ```

3. **Banco de Dados**:

   - Provisionar RDS PostgreSQL
   - Atualizar `spring.datasource.url` com endpoint RDS
   - Configurar Security Groups para acesso do Worker

4. **Networking**:

   - Criar VPC, Subnets, NAT Gateway
   - Configurar VPC Endpoints para AWS services
   - Ajustar Lambda para rodar dentro da VPC

5. **Secrets**:
   - Migrar credenciais para AWS Secrets Manager
   - Usar IAM Roles em vez de access keys hardcoded

---

## 🛡️ Segurança

### Boas Práticas Implementadas

- ✅ Credenciais mock apenas para LocalStack
- ✅ IAM Roles com princípio do menor privilégio
- ✅ Validação de input na Lambda
- ✅ Tratamento de erros sem expor stack traces
- ✅ Logs estruturados sem dados sensíveis

### Melhorias para Produção

- [ ] AWS WAF no API Gateway
- [ ] Encryption at rest (DynamoDB + RDS)
- [ ] TLS 1.3 para todas as conexões
- [ ] AWS KMS para gerenciamento de chaves
- [ ] GuardDuty para detecção de ameaças

---

## 📈 Escalabilidade

### Configurações Atuais

| Componente | Configuração           | Observação                 |
| ---------- | ---------------------- | -------------------------- |
| Lambda     | 128MB RAM, 15s timeout | Pode escalar para 10GB RAM |
| SQS        | Visibility timeout 30s | Suporta até 120k msgs/s    |
| DynamoDB   | PAY_PER_REQUEST        | Auto-scaling habilitado    |
| Worker     | 1 instância, poll 1s   | Escalar horizontalmente    |

### Estratégias de Escala

1. **Lambda**: Concorrência reservada + Provisioned Concurrency
2. **Worker**: Auto Scaling Group baseado em SQS metrics
3. **RDS**: Read Replicas + Aurora Serverless v2
4. **API Gateway**: Throttling e caching por stage

---

## 🔍 Troubleshooting Detalhado

### Problemas Comuns

**1. `./gradlew: No such file or directory`**

```bash
gradle wrapper --gradle-version 8.9
chmod +x gradlew
```

**2. Lambda não encontra handler**

```bash
# Verificar build do JAR
unzip -l lambdas/payment-intake/build/libs/payment-intake-all.jar | grep Handler
```

**3. Worker não consome mensagens**

```bash
# Verificar URL da fila
docker logs payment-worker | grep "Queue URL"

# Testar manualmente
aws --endpoint-url=http://localhost:4566 sqs receive-message \
  --queue-url http://localhost:4566/000000000000/payments-queue
```

**4. API Gateway retorna 500**

```bash
# Verificar logs do Lambda
docker logs localstack | grep payment-intake
```

**5. Comando `make curl` falha**

```bash
# Verificar se awslocal está instalado
which awslocal || pip install awscli-local

# Ou usar aws cli com endpoint
aws --endpoint-url=http://localhost:4566 apigateway get-rest-apis
```

---

## 📚 Referências

### Documentação Oficial

- [Spring Boot 3.3.x](https://docs.spring.io/spring-boot/docs/3.3.x/reference/html/)
- [AWS SDK for Java 2.x](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [LocalStack Docs](https://docs.localstack.cloud/)

### Padrões de Arquitetura

- [AWS Well-Architected Framework](https://aws.amazon.com/architecture/well-architected/)
- [Microservices Patterns - Chris Richardson](https://microservices.io/patterns/)
- [Event-Driven Architecture](https://aws.amazon.com/event-driven-architecture/)

---

## 👤 Autor

**Luis Ismael**  
Projeto desenvolvido como case técnico para processo seletivo Itaú.

### Competências Demonstradas

- ✅ Java 21 + Spring Boot 3.x
- ✅ AWS Services (Lambda, DynamoDB, SNS, SQS, API Gateway)
- ✅ Terraform (Infrastructure as Code)
- ✅ Arquitetura event-driven
- ✅ Containerização (Docker)
- ✅ Migrations com Flyway
- ✅ Gradle Multi-Project
- ✅ Logging e observabilidade
- ✅ Documentação técnica completa

---

## 📄 Licença

Este projeto é de uso educacional e demonstrativo.
