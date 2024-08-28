Expand the name of the chart.
*/}}
{{- define "eric-eo-api-gateway.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create release name used for cluster role.
*/}}
{{- define "eric-eo-api-gateway.release.name" -}}
{{- default .Release.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "eric-eo-api-gateway.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create image registry url
*/}}
{{- define "eric-eo-api-gateway.registryUrl" -}}
{{- if .Values.imageCredentials.registry -}}
{{- if .Values.imageCredentials.registry.url -}}
{{- print .Values.imageCredentials.registry.url -}}
{{- else if .Values.global.registry.url -}}
{{- print .Values.global.registry.url -}}
{{- else -}}
""
{{- end -}}
{{- else if .Values.global.registry.url -}}
{{- print .Values.global.registry.url -}}
{{- else -}}
""
{{- end -}}
{{- end -}}
{{/*
 create prometheus info
*/}}
{{- define "eric-eo-api-gateway.prometheus" -}}
prometheus.io/path: "{{ .Values.prometheus.path }}"
prometheus.io/port: "{{ .Values.prometheus.port }}"
prometheus.io/scrape: "{{ .Values.prometheus.scrape }}"
{{- end -}}

{{/*
Create Ericsson Product Info
*/}}
{{- define "eric-eo-api-gateway.helm-annotations" -}}
ericsson.com/product-name: "EO Api-Gateway Service"
ericsson.com/product-number: "CNX/1020/20"
ericsson.com/product-revision: "NUM"
{{- end}}

{{/*
Create Ericsson product app.kubernetes.io info
*/}}
{{- define "eric-eo-api-gateway.kubernetes-io-info" -}}
app.kubernetes.io/name: {{ .Chart.Name | quote }}
app.kubernetes.io/version: {{ .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" | quote }}
app.kubernetes.io/instance: {{ .Release.Name | quote }}
{{- end -}}
