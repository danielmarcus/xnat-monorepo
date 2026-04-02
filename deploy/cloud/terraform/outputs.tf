# ============================================================================
# XNAT Monorepo — Terraform Outputs
# ============================================================================

output "vpc_id" {
  description = "ID of the VPC created for the XNAT deployment."
  value       = aws_vpc.xnat.id
}

output "subnet_id" {
  description = "ID of the public subnet created for the XNAT instance."
  value       = aws_subnet.xnat_public.id
}

output "internet_gateway_id" {
  description = "ID of the internet gateway attached to the XNAT VPC."
  value       = aws_internet_gateway.xnat.id
}

output "instance_id" {
  description = "The EC2 instance ID of the XNAT server."
  value       = aws_instance.xnat.id
}

output "public_ip" {
  description = "The public IPv4 address of the XNAT instance.  If an Elastic IP is allocated, this is the EIP address; otherwise it is the auto-assigned public IP (which changes on stop/start)."
  value = (
    var.allocate_eip
    ? aws_eip.xnat[0].public_ip
    : aws_instance.xnat.public_ip
  )
}

output "public_dns" {
  description = "The public DNS hostname of the XNAT instance."
  value = (
    var.allocate_eip
    ? aws_eip.xnat[0].public_dns
    : aws_instance.xnat.public_dns
  )
}

output "xnat_url" {
  description = "HTTP URL to reach the XNAT web application."
  value = format(
    "http://%s/xnat",
    var.allocate_eip
      ? aws_eip.xnat[0].public_ip
      : aws_instance.xnat.public_ip
  )
}

output "security_group_id" {
  description = "ID of the security group attached to the XNAT instance."
  value       = aws_security_group.xnat.id
}

output "ami_id" {
  description = "AMI ID used to launch the instance (Amazon Linux 2023)."
  value       = data.aws_ami.amazon_linux_2023.id
}

output "availability_zone" {
  description = "Availability zone in which the instance was launched."
  value       = aws_instance.xnat.availability_zone
}

output "ssh_command" {
  description = "Example SSH command to connect to the instance."
  value = format(
    "ssh -i ~/.ssh/%s.pem ec2-user@%s",
    var.key_name,
    var.allocate_eip
      ? aws_eip.xnat[0].public_ip
      : aws_instance.xnat.public_ip
  )
}
