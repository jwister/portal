# Ztoken Portal

Customer-facing portal for NewAPI. The portal is an independent React and Spring Boot application; it does not modify NewAPI source code or query the NewAPI database.

The backend packages the React production build into a single executable JAR. Run the complete verification suite with:

```powershell
mvn -f backend/pom.xml clean package
```

Start the packaged application with Java 17 and the user-scoped portal session key:

```powershell
.\scripts\start-portal.ps1
```
