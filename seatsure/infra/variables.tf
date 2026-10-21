variable "aws_region" {
  type    = string
  default = "ap-south-1" # Mumbai
}

variable "name" {
  type    = string
  default = "seatsure"
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "enable_nat_gateway" {
  type        = bool
  default     = true
  description = <<-EOT
    true  = production layout: tasks in private subnets, outbound via a NAT gateway (~USD 33/month).
    false = budget layout for learning: tasks in public subnets with public IPs, no NAT.
            Still locked down by security groups (only the ALB can reach them).
  EOT
}

variable "image_tag" {
  type        = string
  default     = "v1"
  description = "Image tag for the FIRST deployment. After that GitHub Actions deploys new tags."
}

variable "backend_desired_count" {
  type        = number
  default     = 1
  description = "Keep at 1 until the WebSocket broker is moved to a relay (RabbitMQ/Amazon MQ). See README."
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_multi_az" {
  type    = bool
  default = false
}

variable "db_deletion_protection" {
  type    = bool
  default = false # set true for anything you care about
}

variable "bedrock_chat_model" {
  type    = string
  default = "global.anthropic.claude-haiku-4-5-20251001-v1:0"
}

variable "bedrock_embedding_model" {
  type    = string
  default = "amazon.titan-embed-text-v2:0"
}

variable "certificate_arn" {
  type        = string
  default     = ""
  description = "Optional ACM certificate ARN. When set, the ALB serves HTTPS and redirects HTTP to it."
}

variable "github_repo" {
  type        = string
  description = "owner/repo allowed to deploy, e.g. iswariya/seatsure"
}

variable "create_github_oidc_provider" {
  type        = bool
  default     = true
  description = "Set false if this AWS account already has the token.actions.githubusercontent.com provider."
}
