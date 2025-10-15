resource "aws_sqs_queue" "payments_queue" {
  name                       = "payments-queue"
  visibility_timeout_seconds = 30
}

resource "aws_sns_topic_subscription" "payments_fanout" {
  topic_arn = aws_sns_topic.payments_topic.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.payments_queue.arn
}

data "aws_iam_policy_document" "sqs_policy" {
  statement {
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.payments_queue.arn]
    
    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }
    
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.payments_topic.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "allow_sns" {
  queue_url = aws_sqs_queue.payments_queue.id
  policy    = data.aws_iam_policy_document.sqs_policy.json
}
