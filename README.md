maven-moduleskipper-plugin
==========================

Maven Extension which is able to remove a project of the current reactor/build session if
the same version is already deployed to a remote repository. 
This is most useful if you have a multi module project where some artifacts have to be released with different classifiers
ans you don't want to fiddle around with the 'skip' option of the deploy plugin.

Please this in the parent pom

```xml
	<build>
		<extensions>
			<extension>
				<groupId>com.fortysix.maven</groupId>
				<artifactId>maven-moduleskipper-plugin</artifactId>
				<version>0.0.1-SNAPSHOT</version>
			</extension>
		</extensions>
	</build>
```

Per default the extension is called for all calls which contain at least the goal: 'deploy'.
The execution can be skipped with '-Dmoduleskipper.skip=true'


