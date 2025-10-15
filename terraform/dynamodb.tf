resource "aws_dynamodb_table" "payments" {
  name         = "payments"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "paymentId"
  
  attribute {
    name = "paymentId"
    type = "S"
  }
}
