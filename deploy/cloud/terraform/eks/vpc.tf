# =============================================================================
# VPC + 2-AZ subnet split for EKS.
#
# EKS control plane requires >=2 subnets in different AZs. We provision:
#   - 2 public subnets  (10.10.0.0/24, 10.10.1.0/24)  — ALB / ELB live here
#   - 2 private subnets (10.10.10.0/24, 10.10.11.0/24) — node group + RDS + EFS
#
# Private subnets reach the internet via NAT gateway(s) in the public subnets.
# Single shared NAT (default, cost-saving) vs one-per-AZ is gated by
# var.single_nat_gateway — production deployments should set false.
#
# Subnet tagging (kubernetes.io/role/elb, kubernetes.io/role/internal-elb,
# kubernetes.io/cluster/<name>=shared) is required so the AWS Load Balancer
# Controller knows which subnets to use for ALB/NLB placement.
# =============================================================================

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs                  = slice(data.aws_availability_zones.available.names, 0, 2)
  public_subnet_cidrs  = ["10.10.0.0/24", "10.10.1.0/24"]
  private_subnet_cidrs = ["10.10.10.0/24", "10.10.11.0/24"]
}

# -----------------------------------------------------------------------------
# VPC
# -----------------------------------------------------------------------------
resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${local.name_prefix}-vpc"
  }
}

# -----------------------------------------------------------------------------
# Public subnets — ALB lives here
# -----------------------------------------------------------------------------
resource "aws_subnet" "public" {
  count                   = length(local.public_subnet_cidrs)
  vpc_id                  = aws_vpc.this.id
  cidr_block              = local.public_subnet_cidrs[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name                                          = "${local.name_prefix}-public-${local.azs[count.index]}"
    "kubernetes.io/role/elb"                      = "1"
    "kubernetes.io/cluster/${local.cluster_name}" = "shared"
  }
}

# -----------------------------------------------------------------------------
# Private subnets — node group, RDS subnet group, EFS mount targets
# -----------------------------------------------------------------------------
resource "aws_subnet" "private" {
  count             = length(local.private_subnet_cidrs)
  vpc_id            = aws_vpc.this.id
  cidr_block        = local.private_subnet_cidrs[count.index]
  availability_zone = local.azs[count.index]

  tags = {
    Name                                          = "${local.name_prefix}-private-${local.azs[count.index]}"
    "kubernetes.io/role/internal-elb"             = "1"
    "kubernetes.io/cluster/${local.cluster_name}" = "shared"
  }
}

# -----------------------------------------------------------------------------
# Internet Gateway + public route table
# -----------------------------------------------------------------------------
resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "${local.name_prefix}-igw"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = {
    Name = "${local.name_prefix}-public"
  }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# -----------------------------------------------------------------------------
# NAT gateway(s) + private route tables
#
# When single_nat_gateway=true, all private subnets share one NAT in the first
# AZ. When false, each private subnet gets its own NAT in its AZ — survives
# AZ outages but doubles NAT cost ($32/mo each at us-east-1 list price).
# -----------------------------------------------------------------------------
resource "aws_eip" "nat" {
  count  = var.single_nat_gateway ? 1 : length(aws_subnet.public)
  domain = "vpc"

  tags = {
    Name = "${local.name_prefix}-nat-${count.index}"
  }
}

resource "aws_nat_gateway" "this" {
  count         = var.single_nat_gateway ? 1 : length(aws_subnet.public)
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = {
    Name = "${local.name_prefix}-nat-${count.index}"
  }

  depends_on = [aws_internet_gateway.this]
}

resource "aws_route_table" "private" {
  count  = length(aws_subnet.private)
  vpc_id = aws_vpc.this.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.this[var.single_nat_gateway ? 0 : count.index].id
  }

  tags = {
    Name = "${local.name_prefix}-private-${local.azs[count.index]}"
  }
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}
