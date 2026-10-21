# ---------- Container registry ----------

resource "aws_ecr_repository" "app" {
  for_each             = toset(["backend", "frontend"])
  name                 = "${var.name}-${each.key}"
  image_tag_mutability = "IMMUTABLE" # a tag always means the same image (we tag with the git SHA)
  force_delete         = true        # lets `terraform destroy` clean up a demo; remove for production

  image_scanning_configuration {
    scan_on_push = true
  }
}

# Keep the last 20 images per repository.
resource "aws_ecr_lifecycle_policy" "app" {
  for_each   = aws_ecr_repository.app
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 20 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}

# ---------- MySQL 8.4 on RDS ----------

resource "aws_db_subnet_group" "main" {
  name       = var.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "mysql" {
  identifier     = var.name
  engine         = "mysql"
  engine_version = "8.4" # major version; RDS picks the latest 8.4.x minor
  instance_class = var.db_instance_class

  allocated_storage     = 20
  max_allocated_storage = 100 # storage autoscaling
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "seatsure"
  username = "seatsure"
  # RDS creates and rotates the password in Secrets Manager. It never appears in Terraform state.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = var.db_multi_az

  backup_retention_period    = 7
  auto_minor_version_upgrade = true
  deletion_protection        = var.db_deletion_protection
  skip_final_snapshot        = !var.db_deletion_protection
  final_snapshot_identifier  = var.db_deletion_protection ? "${var.name}-final" : null
}

# ---------- Application secrets ----------

# 256-bit HMAC key for signing JWTs, base64-encoded as the app expects.
resource "random_bytes" "jwt" {
  length = 32
}

resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${var.name}/jwt-secret"
  recovery_window_in_days = 0 # demo: allow immediate re-create after destroy
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = random_bytes.jwt.base64
}
