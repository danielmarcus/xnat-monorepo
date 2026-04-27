output "eks_cluster_name" {
  description = "Name of the EKS cluster — pass to `aws eks update-kubeconfig`."
  value       = aws_eks_cluster.this.name
}

output "eks_cluster_endpoint" {
  description = "API server endpoint."
  value       = aws_eks_cluster.this.endpoint
}

output "eks_cluster_oidc_issuer_url" {
  description = "OIDC issuer URL for IRSA role trust policies."
  value       = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

output "rds_endpoint" {
  description = "RDS Postgres endpoint hostname (no port). Pass to helm via --set database.host."
  value       = aws_db_instance.xnat.address
}

output "rds_port" {
  description = "RDS port (5432 by convention)."
  value       = aws_db_instance.xnat.port
}

output "efs_id" {
  description = "EFS file system ID — embedded in the efs-sc StorageClass parameters."
  value       = aws_efs_file_system.archive.id
}

output "ecr_repository_url" {
  description = "Full ECR URL for `docker tag` + `helm --set image.repository`."
  value       = aws_ecr_repository.xnat_web.repository_url
}

output "vpc_id" {
  description = "VPC ID — useful for sanity checks and for future modules."
  value       = aws_vpc.this.id
}

output "private_subnet_ids" {
  description = "Private subnets the node group runs in."
  value       = aws_subnet.private[*].id
}

output "kubeconfig_command" {
  description = "Copy-paste command to wire the cluster into your local kubeconfig."
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${aws_eks_cluster.this.name}"
}
