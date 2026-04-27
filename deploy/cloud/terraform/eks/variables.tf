# =============================================================================
# Inputs for the EKS deployment module.
#
# This module is independent of the single-EC2 deployment in
# deploy/cloud/terraform/. They use separate state and resources, by design —
# both deploy targets are meant to coexist (see plan-tomcat10-eks-tests.md).
# =============================================================================

# -----------------------------------------------------------------------------
# AWS / Region
# -----------------------------------------------------------------------------
variable "aws_region" {
  description = "AWS region. Must contain at least 2 AZs available for EKS."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment label (staging | production | dev | qa)."
  type        = string
  default     = "staging"

  validation {
    condition     = contains(["staging", "production", "dev", "qa"], var.environment)
    error_message = "environment must be one of: staging, production, dev, qa."
  }
}

# -----------------------------------------------------------------------------
# Networking
# -----------------------------------------------------------------------------
variable "vpc_cidr" {
  description = "CIDR block for the EKS VPC. Must be /16 to leave room for the subnet split."
  type        = string
  default     = "10.10.0.0/16"
}

variable "single_nat_gateway" {
  description = "When true, use one NAT gateway shared across all AZs (cost-saving for staging). When false, one NAT gateway per AZ (recommended for production)."
  type        = bool
  default     = true
}

# -----------------------------------------------------------------------------
# EKS cluster
# -----------------------------------------------------------------------------
variable "eks_cluster_name" {
  description = "Cluster name. Defaults to xnat-<environment>."
  type        = string
  default     = ""
}

variable "eks_cluster_version" {
  description = "Kubernetes minor version (EKS-supported)."
  type        = string
  default     = "1.30"
}

variable "eks_node_instance_type" {
  description = "Instance type for the managed node group."
  type        = string
  default     = "t3.large"
}

variable "eks_node_min_size" {
  description = "Node group autoscaling minimum. EKS requires >=1, EFS-backed RWX needs >=2 nodes for AZ resilience."
  type        = number
  default     = 2
}

variable "eks_node_max_size" {
  description = "Node group autoscaling maximum."
  type        = number
  default     = 4
}

variable "eks_node_desired_size" {
  description = "Node group desired size at create time. Autoscaler may move this between min/max afterwards."
  type        = number
  default     = 2
}

# -----------------------------------------------------------------------------
# RDS
# -----------------------------------------------------------------------------
variable "db_password" {
  description = "Password for the RDS Postgres `xnat` user. Must be supplied via -var or the EKS_DB_PASSWORD GitHub secret. Never commit a default."
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t3.medium"
}

variable "db_allocated_storage_gb" {
  description = "RDS allocated storage in GiB."
  type        = number
  default     = 50
}

variable "db_multi_az" {
  description = "Whether the RDS instance is multi-AZ. False for staging (cost), true for production."
  type        = bool
  default     = false
}

# -----------------------------------------------------------------------------
# Ingress
# -----------------------------------------------------------------------------
variable "enable_ingress_alb" {
  description = "When true, install the AWS Load Balancer Controller and expect the Helm chart to render an ALB Ingress. When false (default), the Service of type LoadBalancer creates an ELB hostname automatically — no domain needed."
  type        = bool
  default     = false
}

variable "domain_name" {
  description = "Domain to use when enable_ingress_alb=true (e.g. xnat.example.org). Required only with ALB Ingress."
  type        = string
  default     = ""
}

variable "acm_certificate_arn" {
  description = "ACM certificate ARN for the domain. Required only with ALB Ingress."
  type        = string
  default     = ""
}

# -----------------------------------------------------------------------------
# ECR
# -----------------------------------------------------------------------------
variable "ecr_repository_name" {
  description = "ECR repository for the xnat-web image."
  type        = string
  default     = "xnat-web"
}

variable "ecr_image_retain_count" {
  description = "Number of recent images to retain in ECR. Older images are auto-deleted."
  type        = number
  default     = 10
}

# -----------------------------------------------------------------------------
# Computed
# -----------------------------------------------------------------------------
locals {
  cluster_name = var.eks_cluster_name != "" ? var.eks_cluster_name : "xnat-${var.environment}"
  name_prefix  = "xnat-${var.environment}"
}
