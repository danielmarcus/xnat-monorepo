# ============================================================================
# XNAT Monorepo — Terraform: AWS EC2 Cloud Deployment
#
# Provisions a single EC2 instance running Amazon Linux 2023 with Docker and
# Docker Compose pre-installed.  The instance hosts the full XNAT stack
# (PostgreSQL + Tomcat WAR) via the repo's docker-compose.yml.
#
# This configuration creates its own VPC, subnet, internet gateway, and route
# table so it works in any AWS account regardless of whether a default VPC
# exists.
#
# Usage (manual):
#   terraform init   -backend-config=backend.conf
#   terraform plan   -var-file=terraform.tfvars
#   terraform apply  -var-file=terraform.tfvars
#
# See deploy/cloud/scripts/deploy.sh for the WAR-copy + stack-start helper.
# ============================================================================

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Backend is configured at init-time via -backend-config flags or a
  # backend.conf file.  See backend.tf for the backend block.
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "xnat-monorepo"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# ----------------------------------------------------------------------------
# Data — look up the latest Amazon Linux 2023 AMI
# ----------------------------------------------------------------------------
data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# ----------------------------------------------------------------------------
# Data — availability zones in the target region
# ----------------------------------------------------------------------------
data "aws_availability_zones" "available" {
  state = "available"
}

# ----------------------------------------------------------------------------
# VPC
# ----------------------------------------------------------------------------
resource "aws_vpc" "xnat" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "xnat-monorepo-${var.environment}"
  }
}

# ----------------------------------------------------------------------------
# Public Subnet — first AZ in the region
# ----------------------------------------------------------------------------
resource "aws_subnet" "xnat_public" {
  vpc_id                  = aws_vpc.xnat.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = {
    Name = "xnat-monorepo-${var.environment}-public"
  }
}

# ----------------------------------------------------------------------------
# Internet Gateway
# ----------------------------------------------------------------------------
resource "aws_internet_gateway" "xnat" {
  vpc_id = aws_vpc.xnat.id

  tags = {
    Name = "xnat-monorepo-${var.environment}"
  }
}

# ----------------------------------------------------------------------------
# Route Table — default route via IGW
# ----------------------------------------------------------------------------
resource "aws_route_table" "xnat_public" {
  vpc_id = aws_vpc.xnat.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.xnat.id
  }

  tags = {
    Name = "xnat-monorepo-${var.environment}-public"
  }
}

resource "aws_route_table_association" "xnat_public" {
  subnet_id      = aws_subnet.xnat_public.id
  route_table_id = aws_route_table.xnat_public.id
}

# ----------------------------------------------------------------------------
# Security Group
# ----------------------------------------------------------------------------
resource "aws_security_group" "xnat" {
  name        = "xnat-monorepo-${var.environment}"
  description = "Allow SSH, HTTP, and HTTPS access to the XNAT instance"
  vpc_id      = aws_vpc.xnat.id

  # SSH — restricted to operator CIDR to reduce attack surface
  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.ssh_allowed_cidr_blocks
  }

  # HTTP
  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # HTTPS
  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # All outbound traffic allowed (for package downloads, artifact pulls, etc.)
  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "xnat-monorepo-${var.environment}"
  }
}

# ----------------------------------------------------------------------------
# EC2 Instance
# ----------------------------------------------------------------------------
resource "aws_instance" "xnat" {
  ami                         = data.aws_ami.amazon_linux_2023.id
  instance_type               = var.instance_type
  key_name                    = var.key_name
  subnet_id                   = aws_subnet.xnat_public.id
  vpc_security_group_ids      = [aws_security_group.xnat.id]
  associate_public_ip_address = true

  # Ensure the root volume is large enough for Docker images + XNAT data
  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size_gb
    delete_on_termination = true
    encrypted             = true
  }

  # User-data: installs Docker, Docker Compose v2, and pulls the deploy script
  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    xnat_version     = var.xnat_version
    deploy_s3_bucket = var.deploy_s3_bucket
    xnat_admin_pass  = var.xnat_admin_password
    environment      = var.environment
    COMPOSE_VERSION  = "2.29.1"
  })

  user_data_replace_on_change = true

  tags = {
    Name        = "xnat-monorepo-${var.environment}"
    XnatVersion = var.xnat_version
    GitSha      = var.git_sha
  }

  lifecycle {
    # Prevent accidental destruction of long-running instances
    prevent_destroy = false
  }
}

# ----------------------------------------------------------------------------
# Elastic IP (optional, enabled when var.allocate_eip = true)
# ----------------------------------------------------------------------------
resource "aws_eip" "xnat" {
  count    = var.allocate_eip ? 1 : 0
  instance = aws_instance.xnat.id
  domain   = "vpc"

  tags = {
    Name = "xnat-monorepo-${var.environment}"
  }
}

# ============================================================================
# Inline user_data template (written as a local file to keep main.tf readable)
# ============================================================================
resource "local_file" "user_data_rendered" {
  count    = 0  # set to 1 to write rendered user-data to disk for inspection
  filename = "${path.module}/.rendered_user_data.sh"
  content  = templatefile("${path.module}/user_data.sh.tpl", {
    xnat_version     = var.xnat_version
    deploy_s3_bucket = var.deploy_s3_bucket
    xnat_admin_pass  = var.xnat_admin_password
    environment      = var.environment
  })
}
