output "app_url" {
  description = "Open this in a browser"
  value       = local.public_origin
}

output "ecr_backend" {
  value = aws_ecr_repository.app["backend"].repository_url
}

output "ecr_frontend" {
  value = aws_ecr_repository.app["frontend"].repository_url
}

output "github_deploy_role_arn" {
  description = "Put this in the GitHub repo variable AWS_DEPLOY_ROLE_ARN"
  value       = aws_iam_role.github_deploy.arn
}

output "ecs_cluster" {
  value = aws_ecs_cluster.main.name
}

output "backend_service" {
  value = aws_ecs_service.backend.name
}

output "frontend_service" {
  value = aws_ecs_service.frontend.name
}

output "rds_endpoint" {
  value = aws_db_instance.mysql.address
}
