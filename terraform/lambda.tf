resource "aws_iam_role" "lambda_role" {
  name = "lambda-exec-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
      Effect = "Allow"
    }]
  })
}

resource "aws_lambda_function" "payment_intake" {
  function_name = "payment-intake"
  role          = aws_iam_role.lambda_role.arn
  handler       = "com.itau.challenge.PaymentIntakeHandler::handleRequest"
  runtime       = "java21"
  filename      = "${path.module}/../build/lambdas/payment-intake.zip"
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
