{{- define "rustfs.labels" -}}
app.kubernetes.io/name: rustfs
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
