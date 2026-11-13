{{- define "gorse.labels" -}}
app.kubernetes.io/name: gorse
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
