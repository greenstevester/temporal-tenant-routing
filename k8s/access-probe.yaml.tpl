apiVersion: batch/v1
kind: Job
metadata:
  name: access-probe-__RUN__
  namespace: tenant-b
spec:
  backoffLimit: 0
  activeDeadlineSeconds: 90
  template:
    metadata:
      labels:
        app: access-probe
    spec:
      restartPolicy: Never
      serviceAccountName: temporal-worker
      automountServiceAccountToken: false
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: probe
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
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: probe
            - name: TENANT_A_TARGET
              value: __TARGET_A__
            - name: TENANT_B_TARGET
              value: __TARGET_B__
            - name: TENANT_A_NAMESPACE
              value: __NAMESPACE_A__
            - name: TENANT_B_NAMESPACE
              value: __NAMESPACE_B__
            - name: TEMPORAL_API_KEY
              valueFrom:
                secretKeyRef:
                  name: temporal-worker-credentials
                  key: api-key
            - name: LOCAL_TEMPORAL
              value: "__LOCAL__"
            - name: EXPECT_DENIED
              value: "__DENIED__"
            - name: PROBE_WORKFLOW_ID
              value: tenant-a/job/__JOB__
          volumeMounts:
            - name: temporary
              mountPath: /tmp
      volumes:
        - name: temporary
          emptyDir: {}
