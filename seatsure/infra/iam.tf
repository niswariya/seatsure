# Two roles per task, a classic interview question:
#  - EXECUTION role: used by the ECS agent BEFORE the app starts (pull image, read secrets, write logs).
#  - TASK role: used by the application code at runtime (here: call Bedrock). No access keys anywhere.

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# ---------- Execution role ----------

resource "aws_iam_role" "execution" {
  name               = "${var.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_secrets" {
  statement {
    sid     = "ReadAppSecrets"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      aws_db_instance.mysql.master_user_secret[0].secret_arn,
      aws_secretsmanager_secret.jwt.arn,
    ]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "read-app-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

# ---------- Task role (application) ----------

resource "aws_iam_role" "backend_task" {
  name               = "${var.name}-backend-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "bedrock_invoke" {
  statement {
    sid = "InvokeChatAndEmbeddingModels"
    # The Converse API is authorised with the InvokeModel actions.
    actions = ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream"]
    resources = [
      # Cross-Region inference profiles in this account (e.g. global.anthropic.claude-...)
      "arn:aws:bedrock:*:${data.aws_caller_identity.current.account_id}:inference-profile/*",
      # The foundation models a profile can route to (any Region, incl. the Region-less global ARN)
      "arn:aws:bedrock:*::foundation-model/anthropic.claude-*",
      "arn:aws:bedrock:*::foundation-model/amazon.titan-embed-text-v2*",
    ]
  }
}

resource "aws_iam_role_policy" "backend_bedrock" {
  name   = "bedrock-invoke"
  role   = aws_iam_role.backend_task.id
  policy = data.aws_iam_policy_document.bedrock_invoke.json
}

# The frontend (nginx) needs no AWS permissions at all.
resource "aws_iam_role" "frontend_task" {
  name               = "${var.name}-frontend-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}
