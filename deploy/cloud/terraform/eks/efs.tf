# =============================================================================
# EFS for the XNAT archive (RWX).
#
# XNAT stores all session data under /data/xnat/archive. EBS is RWO — fine for
# a 1-replica deployment but the moment scale-out is needed (or a recreate
# rolling deployment that wants the new pod to start before the old one
# releases its volume), RWO blocks it. EFS gives RWX without redesign.
#
# One mount target per AZ — the EFS CSI driver picks the AZ-local mount when
# scheduling a pod's NFS mount.
# =============================================================================

resource "aws_security_group" "efs" {
  name        = "${local.name_prefix}-efs"
  description = "Allow NFS 2049 from the EKS node group only"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "NFS from EKS nodes"
    from_port       = 2049
    to_port         = 2049
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
    Name = "${local.name_prefix}-efs"
  }
}

resource "aws_efs_file_system" "archive" {
  creation_token   = "${local.name_prefix}-archive"
  encrypted        = true
  performance_mode = "generalPurpose"
  throughput_mode  = "bursting"

  lifecycle_policy {
    transition_to_ia = "AFTER_30_DAYS"
  }

  tags = {
    Name = "${local.name_prefix}-archive"
  }
}

resource "aws_efs_mount_target" "archive" {
  count           = length(aws_subnet.private)
  file_system_id  = aws_efs_file_system.archive.id
  subnet_id       = aws_subnet.private[count.index].id
  security_groups = [aws_security_group.efs.id]
}
