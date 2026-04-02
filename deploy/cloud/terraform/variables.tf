# ============================================================================
# XNAT Monorepo — Terraform Variables
# ============================================================================

# ----------------------------------------------------------------------------
# AWS / Region
# ----------------------------------------------------------------------------
variable "aws_region" {
  description = "AWS region in which to deploy the XNAT instance."
  type        = string
  default     = "us-east-1"
}

# ----------------------------------------------------------------------------
# Networking
# ----------------------------------------------------------------------------
variable "vpc_id" {
  description = "ID of the VPC in which to create the security group and instance."
  type        = string
  # No default — must be supplied via tfvars or -var flag
}

variable "subnet_id" {
  description = "ID of the subnet in which to launch the EC2 instance.  Must be within vpc_id."
  type        = string
  # No default — must be supplied
}

variable "ssh_allowed_cidr_blocks" {
  description = "List of CIDR blocks allowed to reach port 22 (SSH).  Restrict to your operator IP(s)."
  type        = list(string)
  default     = ["0.0.0.0/0"]  # Open by default; tighten in production
}

# ----------------------------------------------------------------------------
# EC2 / Compute
# ----------------------------------------------------------------------------
variable "instance_type" {
  description = "EC2 instance type.  t3.medium is the minimum for a usable XNAT instance."
  type        = string
  default     = "t3.medium"

  validation {
    condition     = can(regex("^[a-z][0-9][a-z]?\\.", var.instance_type))
    error_message = "instance_type must be a valid EC2 instance type string (e.g. t3.medium, m6i.large)."
  }
}

variable "key_name" {
  description = "Name of the EC2 key pair to associate with the instance for SSH access."
  type        = string
}

variable "root_volume_size_gb" {
  description = "Size of the root EBS volume in GiB.  Must be at least 30 GiB to accommodate Docker images and XNAT data."
  type        = number
  default     = 50

  validation {
    condition     = var.root_volume_size_gb >= 30
    error_message = "root_volume_size_gb must be at least 30 GiB."
  }
}

variable "allocate_eip" {
  description = "Whether to allocate an Elastic IP and associate it with the instance.  Recommended for production so the public IP is stable across stop/start cycles."
  type        = bool
  default     = true
}

# ----------------------------------------------------------------------------
# Environment / Tagging
# ----------------------------------------------------------------------------
variable "environment" {
  description = "Deployment environment label applied to all resource tags (e.g. staging, production)."
  type        = string
  default     = "staging"

  validation {
    condition     = contains(["staging", "production", "dev", "qa"], var.environment)
    error_message = "environment must be one of: staging, production, dev, qa."
  }
}

# ----------------------------------------------------------------------------
# XNAT Application
# ----------------------------------------------------------------------------
variable "xnat_version" {
  description = "XNAT version string, used for tagging and identifying the deployed WAR (e.g. 1.10.0-RC2-SNAPSHOT)."
  type        = string
  default     = "1.10.0-RC2-SNAPSHOT"
}

variable "xnat_admin_password" {
  description = "Initial admin password for the XNAT instance.  Passed to Docker Compose via the user-data template.  Must be changed after first login."
  type        = string
  sensitive   = true
  default     = "changeme"
}

variable "git_sha" {
  description = "Git commit SHA of the build being deployed.  Used for EC2 instance tagging and traceability."
  type        = string
  default     = "unknown"
}

# ----------------------------------------------------------------------------
# S3 / Artefact staging
# ----------------------------------------------------------------------------
variable "deploy_s3_bucket" {
  description = "Name of the S3 bucket used to stage the WAR and Docker Compose files before the instance starts.  Leave empty to skip S3 staging."
  type        = string
  default     = ""
}
