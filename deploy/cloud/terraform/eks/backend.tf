# =============================================================================
# S3 remote backend for the EKS module.
#
# Separate state file from the single-EC2 module — supply a different `key`
# at init time so they don't collide. CI passes:
#   -backend-config="bucket=${{ secrets.TF_BACKEND_BUCKET }}"
#   -backend-config="key=xnat/eks/terraform.tfstate"
#   -backend-config="region=${{ secrets.TF_BACKEND_REGION }}"
#
# For manual use, create eks-backend.conf in this directory:
#
#   bucket         = "my-xnat-terraform-state"
#   key            = "xnat/eks/terraform.tfstate"
#   region         = "us-east-1"
#   encrypt        = true
#   dynamodb_table = "xnat-terraform-locks"
#
# Then init with:
#   terraform init -backend-config=eks-backend.conf
# =============================================================================

terraform {
  backend "s3" {
    encrypt = true
  }
}
