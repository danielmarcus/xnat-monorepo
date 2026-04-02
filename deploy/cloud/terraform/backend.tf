# ============================================================================
# XNAT Monorepo — Terraform S3 Remote Backend
#
# The backend block itself cannot use Terraform variables, so the bucket name,
# key, and region are supplied at `terraform init` time via -backend-config
# flags or a backend.conf file.
#
# CI (cloud-deploy.yml) passes:
#   -backend-config="bucket=${{ secrets.TF_BACKEND_BUCKET }}"
#   -backend-config="key=${{ secrets.TF_BACKEND_KEY }}"
#   -backend-config="region=${{ secrets.TF_BACKEND_REGION }}"
#
# For manual use, create a file called backend.conf in this directory:
#
#   bucket         = "my-xnat-terraform-state"
#   key            = "xnat/terraform.tfstate"
#   region         = "us-east-1"
#   encrypt        = true
#   dynamodb_table = "xnat-terraform-locks"   # optional — enables state locking
#
# Then initialise with:
#   terraform init -backend-config=backend.conf
#
# The backend.conf file contains only non-sensitive S3 metadata and may be
# committed.  Do NOT commit actual AWS credentials.
# ============================================================================

terraform {
  backend "s3" {
    # All values must be supplied at init time via -backend-config.
    # The keys below are placeholders that document what is expected.
    #
    # bucket         = "<TF_BACKEND_BUCKET>"
    # key            = "<TF_BACKEND_KEY>"        # e.g. "xnat/terraform.tfstate"
    # region         = "<TF_BACKEND_REGION>"     # e.g. "us-east-1"
    # encrypt        = true
    # dynamodb_table = "<optional-lock-table>"

    encrypt = true  # Always encrypt state at rest
  }
}
