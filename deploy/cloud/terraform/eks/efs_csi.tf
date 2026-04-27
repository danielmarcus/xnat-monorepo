# =============================================================================
# AWS EFS CSI driver — installed via Helm so the chart can use efs-sc.
#
# We don't need IRSA for the static-provisioning path the XNAT chart uses
# (the chart references a pre-created EFS file system by ID). The CSI driver
# DOES need an IAM role for *dynamic* PV provisioning, which we wire up so
# anyone who later wants `efs-sc` to dynamically provision EFS access points
# without modifying terraform has it ready.
# =============================================================================

data "aws_iam_policy_document" "efs_csi_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.eks.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(aws_iam_openid_connect_provider.eks.url, "https://", "")}:sub"
      values   = ["system:serviceaccount:kube-system:efs-csi-controller-sa"]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(aws_iam_openid_connect_provider.eks.url, "https://", "")}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "efs_csi" {
  name               = "${local.name_prefix}-efs-csi"
  assume_role_policy = data.aws_iam_policy_document.efs_csi_assume.json
}

resource "aws_iam_role_policy_attachment" "efs_csi" {
  role       = aws_iam_role.efs_csi.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEFSCSIDriverPolicy"
}

resource "kubernetes_service_account" "efs_csi" {
  metadata {
    name      = "efs-csi-controller-sa"
    namespace = "kube-system"
    annotations = {
      "eks.amazonaws.com/role-arn" = aws_iam_role.efs_csi.arn
    }
  }
}

resource "helm_release" "efs_csi" {
  name       = "aws-efs-csi-driver"
  namespace  = "kube-system"
  repository = "https://kubernetes-sigs.github.io/aws-efs-csi-driver/"
  chart      = "aws-efs-csi-driver"
  version    = "3.0.7"

  set {
    name  = "controller.serviceAccount.create"
    value = "false"
  }
  set {
    name  = "controller.serviceAccount.name"
    value = kubernetes_service_account.efs_csi.metadata[0].name
  }

  depends_on = [aws_eks_node_group.this]
}

# -----------------------------------------------------------------------------
# StorageClass — chart's archive PVC references this by name.
# Static provisioning: file system ID is fixed; the CSI driver doesn't create
# new EFS file systems per PVC.
# -----------------------------------------------------------------------------
resource "kubernetes_storage_class" "efs" {
  metadata {
    name = "efs-sc"
  }
  storage_provisioner = "efs.csi.aws.com"
  reclaim_policy      = "Retain"
  volume_binding_mode = "Immediate"

  parameters = {
    provisioningMode = "efs-ap"
    fileSystemId     = aws_efs_file_system.archive.id
    directoryPerms   = "0755"
  }

  depends_on = [helm_release.efs_csi]
}
