# =============================================================================
# ECR repository for the xnat-web image.
#
# The eks-deploy script builds the image with `docker build` against
# Dockerfile.k8s (which bakes the WAR in), tags it with the Git SHA, pushes
# here, and `helm upgrade` references this URL via image.repository.
# =============================================================================

resource "aws_ecr_repository" "xnat_web" {
  name                 = var.ecr_repository_name
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

# Lifecycle: keep the most recent N images; delete older.
resource "aws_ecr_lifecycle_policy" "xnat_web" {
  repository = aws_ecr_repository.xnat_web.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last ${var.ecr_image_retain_count} images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.ecr_image_retain_count
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
