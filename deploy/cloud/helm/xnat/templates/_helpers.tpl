{{/* vim: set filetype=mustache: */}}

{{/*
Expand the name of the chart.
*/}}
{{- define "xnat.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fully qualified app name (release-prefixed; capped at 63 chars per DNS-1123).
*/}}
{{- define "xnat.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Chart label (chart name + version, dash-joined and DNS-safe).
*/}}
{{- define "xnat.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels — applied to every resource.
*/}}
{{- define "xnat.labels" -}}
helm.sh/chart: {{ include "xnat.chart" . }}
{{ include "xnat.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels — used by Service spec.selector and Deployment spec.selector.matchLabels.
Must be stable across chart upgrades.
*/}}
{{- define "xnat.selectorLabels" -}}
app.kubernetes.io/name: {{ include "xnat.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Service account name — uses the override if set, otherwise the fullname.
*/}}
{{- define "xnat.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "xnat.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Resolve the database host. When inClusterPostgres is enabled, that takes
precedence over .Values.database.host so operators can flip the toggle
without also having to clear `host`. The in-cluster Service is named
`<release>-postgres` (see postgres-service.yaml).
*/}}
{{- define "xnat.databaseHost" -}}
{{- if .Values.inClusterPostgres.enabled -}}
{{ include "xnat.fullname" . }}-postgres
{{- else -}}
{{ .Values.database.host }}
{{- end -}}
{{- end }}

{{/*
Name of the Secret holding the DB password. When existingSecretName is set,
use it; otherwise default to the chart-managed name.
*/}}
{{- define "xnat.dbSecretName" -}}
{{- if .Values.database.existingSecretName -}}
{{ .Values.database.existingSecretName }}
{{- else -}}
{{ include "xnat.fullname" . }}-db
{{- end -}}
{{- end }}
