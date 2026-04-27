# =============================================================================
# RDS Postgres 15 for XNAT.
#
# Reachable only from within the EKS node security group. Subnet group spans
# the two private subnets. multi_az is var-controlled — false for staging,
# true for production. final_snapshot is skipped to keep terraform destroy
# fast in non-prod; flip the var when you stand up production.
# =============================================================================

resource "aws_db_subnet_group" "this" {
  name        = "${local.name_prefix}-db"
  subnet_ids  = aws_subnet.private[*].id
  description = "Private subnets for the XNAT RDS instance"

  tags = {
    Name = "${local.name_prefix}-db"
  }
}

# Security group — only the EKS node group can reach 5432. We attach the
# node-group SG (auto-managed by EKS) as the source.
resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds"
  description = "Allow Postgres 5432 from the EKS node group only"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "Postgres from EKS nodes"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_eks_cluster.this.vpc_config[0].cluster_security_group_id]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-rds"
  }
}

resource "aws_db_instance" "xnat" {
  identifier                   = "${local.name_prefix}-xnat"
  engine                       = "postgres"
  engine_version               = "15"
  instance_class               = var.db_instance_class
  allocated_storage            = var.db_allocated_storage_gb
  storage_type                 = "gp3"
  storage_encrypted            = true
  db_name                      = "xnat"
  username                     = "xnat"
  password                     = var.db_password
  port                         = 5432
  db_subnet_group_name         = aws_db_subnet_group.this.name
  vpc_security_group_ids       = [aws_security_group.rds.id]
  publicly_accessible          = false
  multi_az                     = var.db_multi_az
  backup_retention_period      = 7
  backup_window                = "03:00-04:00"
  maintenance_window           = "sun:04:00-sun:05:00"
  auto_minor_version_upgrade   = true
  deletion_protection          = false
  skip_final_snapshot          = true
  apply_immediately            = false
  performance_insights_enabled = true

  tags = {
    Name = "${local.name_prefix}-xnat"
  }
}
