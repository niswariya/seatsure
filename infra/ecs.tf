resource "aws_ecs_cluster" "main" {
  name = var.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_cloudwatch_log_group" "app" {
  for_each          = toset(["backend", "frontend"])
  name              = "/ecs/${var.name}-${each.key}"
  retention_in_days = 14
}

locals {
  public_origin = local.https ? "https://${aws_lb.main.dns_name}" : "http://${aws_lb.main.dns_name}"
  db_url        = "jdbc:mysql://${aws_db_instance.mysql.address}:${aws_db_instance.mysql.port}/seatsure?sslMode=REQUIRED&serverTimezone=UTC"
}

# ---------- Backend: Spring Boot + Spring AI ----------

resource "aws_ecs_task_definition" "backend" {
  family                   = "${var.name}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.backend_task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = "backend"
    image        = "${aws_ecr_repository.app["backend"].repository_url}:${var.image_tag}"
    essential    = true
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "aws" }, # switches Spring AI + Bedrock on
      { name = "AWS_REGION", value = var.aws_region },
      { name = "DB_URL", value = local.db_url },
      { name = "DB_USERNAME", value = aws_db_instance.mysql.username },
      { name = "CORS_ORIGINS", value = local.public_origin },
      { name = "BEDROCK_CHAT_MODEL", value = var.bedrock_chat_model },
      { name = "BEDROCK_EMBEDDING_MODEL", value = var.bedrock_embedding_model },
    ]

    # Injected by ECS from Secrets Manager at start-up; never stored in the task definition.
    secrets = [
      { name = "DB_PASSWORD", valueFrom = "${aws_db_instance.mysql.master_user_secret[0].secret_arn}:password::" },
      { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt.arn },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app["backend"].name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "backend"
      }
    }
  }])
}

resource "aws_ecs_service" "backend" {
  name            = "${var.name}-backend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.backend_desired_count
  launch_type     = "FARGATE"

  # Spring Boot start-up + Flyway + embedding the help centre takes a while.
  health_check_grace_period_seconds = 180

  network_configuration {
    subnets          = local.task_subnet_ids
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = local.task_public_ip
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "backend"
    container_port   = 8080
  }

  # A deployment whose tasks keep failing health checks is rolled back automatically.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # GitHub Actions registers new task definitions on every deploy; don't let Terraform undo that.
  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [aws_lb_listener_rule.backend]
}

# ---------- Frontend: nginx serving the React build ----------

resource "aws_ecs_task_definition" "frontend" {
  family                   = "${var.name}-frontend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.frontend_task.arn

  container_definitions = jsonencode([{
    name         = "frontend"
    image        = "${aws_ecr_repository.app["frontend"].repository_url}:${var.image_tag}"
    essential    = true
    portMappings = [{ containerPort = 80, protocol = "tcp" }]
    environment  = [{ name = "BACKEND_URL", value = local.public_origin }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app["frontend"].name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "frontend"
      }
    }
  }])
}

resource "aws_ecs_service" "frontend" {
  name            = "${var.name}-frontend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.frontend.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = local.task_subnet_ids
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = local.task_public_ip
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.frontend.arn
    container_name   = "frontend"
    container_port   = 80
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  lifecycle {
    ignore_changes = [task_definition]
  }
}
