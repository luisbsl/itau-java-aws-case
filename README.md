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
- ✅ Testes e qualidade de código
- ✅ Containerização e ambiente local

---

## 🏗️ Arquitetura do Sistema

### Visão Geral

```
┌─────────────┐      ┌──────────────┐      ┌─────────┐      ┌─────┐
│ API Gateway │─────▶│ Lambda       │─────▶│ SNS     │─────▶│ SQS │
│             │      │ (payment-    │      │ Topic   │      │Queue│
└─────────────┘      │  intake)     │      └─────────┘      └──┬──┘
                     └──────┬───────┘                          │
                            │                                   │
                            ▼                                   ▼
                     ┌──────────────┐              ┌────────────────┐
                     │  DynamoDB    │              │ Payment Worker │
                     │  (payments)  │              │  (Spring Boot) │
                     └──────────────┘              └───────┬────────┘
                                                           │
                     ┌──────────────┐                     ▼
                     │ Payment API  │◀─────────┌───────────────┐
                     │(Spring Boot) │          │  PostgreSQL   │
                     └──────────────┘          │  (RDS local)  │
                                               └───────────────┘
```

### Fluxo de Dados

1. **Ingestão**: Cliente envia POST `/payments` via API Gateway
2. **Processamento Síncrono**: Lambda valida e persiste no DynamoDB
3. **Publicação**: Lambda publica evento no SNS Topic
4. **Distribuição**: SNS encaminha para SQS Queue (fanout pattern)
5. **Consumo Assíncrono**: Worker Spring Boot consome da fila
6. **Persistência**: Dados armazenados em PostgreSQL (audit/analytics)
7. **Consulta**: Payment API permite leitura via GET `/payments/{id}`

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
  # ...
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
- `GET /payments/{id}` - Consulta pagamento por ID no DynamoDB

**Tecnologias**:

- Spring Boot 3.3.3
- Spring Web
- AWS SDK v2 (DynamoDB)
- Spring Actuator

**Exemplo de Resposta**:

```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": "49.90",
  "customerId": "C777",
  "status": "PENDING"
}
```

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

# Instalar Gradle (via SDKMAN)
sdk install gradle 8.9

# Verificar Docker
docker --version && docker compose version
```

### Setup Completo

```bash
# 1. Subir infraestrutura local
docker compose up -d

# 2. Gerar Gradle Wrapper
gradle wrapper --gradle-version 8.9
chmod +x gradlew

# 3. Compilar Lambda
./gradlew :lambdas:payment-intake:shadowJar

# 4. Empacotar e provisionar infraestrutura
make package-lambda
make apply

# 5. Executar serviços Spring Boot (em terminais separados)
./gradlew :services:payment-worker:bootRun
./gradlew :services:payment-api:bootRun
```

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

### Estrutura de Testes

```bash
# Executar todos os testes
./gradlew test

# Testes por módulo
./gradlew :lambdas:payment-intake:test
./gradlew :services:payment-worker:test
./gradlew :services:payment-api:test
```

### Cobertura

- **Lambda**: Testes unitários com JUnit 5
- **Worker**: Testes de integração com Testcontainers (PostgreSQL)
- **API**: Testes de controller com MockMvc

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

1. **Código**:
   - Remover variáveis `LOCALSTACK*` dos handlers
   - Configurar `endpointOverride` condicional via env vars
2. **Terraform**:

   ```hcl
   # Remover bloco endpoints {} do provider
   provider "aws" {
     region = "us-east-1"
     # Usar credenciais reais (AWS CLI / IAM Roles)
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

---

## 📄 Licença

Este projeto é de uso educacional e demonstrativo.
