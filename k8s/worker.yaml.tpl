apiVersion: v1
kind: ServiceAccount
metadata:
  name: temporal-worker
  namespace: __TENANT__
automountServiceAccountToken: false
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: temporal-worker
  namespace: __TENANT__
spec:
  replicas: 2
  selector:
    matchLabels:
      app: temporal-worker
  template:
    metadata:
      labels:
        app: temporal-worker
    spec:
      serviceAccountName: temporal-worker
      automountServiceAccountToken: false
      terminationGracePeriodSeconds: 30
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: worker
          image: __IMAGE__
          imagePullPolicy: Never
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: ["ALL"]
          resources:
            requests:
              cpu: "100m"
              memory: "256Mi"
            limits:
              cpu: "1"
              memory: "512Mi"
          volumeMounts:
            - name: temporary
              mountPath: /tmp
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: worker
            - name: TENANT_ID
              value: __TENANT__
            - name: TEMPORAL_NAMESPACE
              value: __NAMESPACE__
            - name: TEMPORAL_TASK_QUEUE
              value: __TENANT__-work
            - name: TEMPORAL_TARGET
              value: __TARGET__
            - name: LOCAL_TEMPORAL
              value: "__LOCAL__"
            - name: TEMPORAL_API_KEY
              valueFrom:
                secretKeyRef:
                  name: temporal-worker-credentials
                  key: api-key
            - name: POD_NAMESPACE
              valueFrom:
                fieldRef:
                  fieldPath: metadata.namespace
            - name: POD_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
      volumes:
        - name: temporary
          emptyDir: {}
