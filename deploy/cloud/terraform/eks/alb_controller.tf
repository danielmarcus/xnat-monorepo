# =============================================================================
# AWS Load Balancer Controller — only when var.enable_ingress_alb is true.
#
# When false (default), the chart's Service of type LoadBalancer is enough:
# AWS provisions a classic ELB, returns a hostname like a1b2c3.elb.amazonaws.com,
# and the operator points DNS at that. No ALB controller needed for that path.
#
# When true, this module installs the AWS LB Controller via Helm and wires the
# IRSA-based service account so the chart's Ingress can request an ALB with an
# ACM cert.
# =============================================================================

# IAM policy doc bundled with the controller. We embed the trust policy here
# rather than fetching from the upstream URL so terraform plan stays
# deterministic offline.
data "aws_iam_policy_document" "alb_controller_assume" {
  count = var.enable_ingress_alb ? 1 : 0

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
      values   = ["system:serviceaccount:kube-system:aws-load-balancer-controller"]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(aws_iam_openid_connect_provider.eks.url, "https://", "")}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "alb_controller" {
  count              = var.enable_ingress_alb ? 1 : 0
  name               = "${local.name_prefix}-alb-controller"
  assume_role_policy = data.aws_iam_policy_document.alb_controller_assume[0].json
}

# The official AWS Load Balancer Controller IAM policy is too large to inline
# here cleanly (~10 KB). Reference it by managed-policy ARN. The policy is
# downloaded into the AWS account at apply time via the data source.
data "http" "alb_controller_policy" {
  count = var.enable_ingress_alb ? 1 : 0
  url   = "https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.8.2/docs/install/iam_policy.json"
}

resource "aws_iam_policy" "alb_controller" {
  count       = var.enable_ingress_alb ? 1 : 0
  name        = "${local.name_prefix}-alb-controller"
  description = "AWS Load Balancer Controller policy for ${local.cluster_name}"
  policy      = data.http.alb_controller_policy[0].response_body
}

resource "aws_iam_role_policy_attachment" "alb_controller" {
  count      = var.enable_ingress_alb ? 1 : 0
  role       = aws_iam_role.alb_controller[0].name
  policy_arn = aws_iam_policy.alb_controller[0].arn
}

resource "kubernetes_service_account" "alb_controller" {
  count = var.enable_ingress_alb ? 1 : 0

  metadata {
    name      = "aws-load-balancer-controller"
    namespace = "kube-system"
    annotations = {
      "eks.amazonaws.com/role-arn" = aws_iam_role.alb_controller[0].arn
    }
  }
}

resource "helm_release" "alb_controller" {
  count      = var.enable_ingress_alb ? 1 : 0
  name       = "aws-load-balancer-controller"
  namespace  = "kube-system"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = "1.8.2"

  set {
    name  = "clusterName"
    value = aws_eks_cluster.this.name
  }
  set {
    name  = "serviceAccount.create"
    value = "false"
  }
  set {
    name  = "serviceAccount.name"
    value = kubernetes_service_account.alb_controller[0].metadata[0].name
  }
  set {
    name  = "vpcId"
    value = aws_vpc.this.id
  }
  set {
    name  = "region"
    value = var.aws_region
  }

  depends_on = [aws_eks_node_group.this]
}
