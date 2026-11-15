apiVersion: v1
kind: ServiceAccount
metadata:
  name: tenant-application
  namespace: application
automountServiceAccountToken: false
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: tenant-application
  namespace: application
spec:
  replicas: 1
  selector:
    matchLabels:
      app: tenant-application
  template:
    metadata:
      labels:
        app: tenant-application
    spec:
      serviceAccountName: tenant-application
      automountServiceAccountToken: false
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: application
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
          ports:
            - containerPort: 8080
          readinessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 5
          startupProbe:
            httpGet:
              path: /health
              port: 8080
            failureThreshold: 60
            periodSeconds: 2
          envFrom:
            - secretRef:
                name: application-credentials
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: app
            - name: TENANT_A_TARGET
              value: __TARGET_A__
            - name: TENANT_B_TARGET
              value: __TARGET_B__
            - name: TENANT_A_NAMESPACE
              value: __NAMESPACE_A__
            - name: TENANT_B_NAMESPACE
              value: __NAMESPACE_B__
            - name: LOCAL_TEMPORAL
              value: "__LOCAL__"
          volumeMounts:
            - name: temporary
              mountPath: /tmp
      volumes:
        - name: temporary
          emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: tenant-application
  namespace: application
spec:
  selector:
    app: tenant-application
  ports:
    - port: 8080
      targetPort: 8080
