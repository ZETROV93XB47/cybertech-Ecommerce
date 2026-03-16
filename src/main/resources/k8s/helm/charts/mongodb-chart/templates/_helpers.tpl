{{- define "mongodb.name" -}}
{{- default .Chart.Name .Values.nameOverride -}}
{{- end -}}

{{- define "mongodb.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version -}}
{{- end -}}

{{- define "mongodb.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "mongodb.name" .) -}}
{{- end -}}

