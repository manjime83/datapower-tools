<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:dp="http://www.datapower.com/extensions" xmlns:date="http://exslt.org/dates-and-times" extension-element-prefixes="dp date" exclude-result-prefixes="dp date">

	<xsl:template match="/">

		<xsl:variable name="vars">
			<xsl:variable name="time-value" select="dp:time-value()" />
			<audit>
				<username>
					<xsl:value-of select="substring-after(dp:variable('var://context/WSM/identity/username'), ']')" />
				</username>
				<scope>
					<xsl:value-of select="dp:variable('var://context/WSM/resource/mapped-resource')" />
				</scope>
				<uuid>
					<xsl:value-of select="dp:variable('var://context/api.gateway/uuid')" />
				</uuid>
				<device-name>
					<xsl:value-of select="string(dp:variable('var://service/system/ident')/identification/device-name)" />
				</device-name>
			</audit>
		</xsl:variable>

		<xsl:variable name="statement">
			UPDATE OAUTH_EP_AUDIT SET USERNAME = ?, SCOPE = ? WHERE UUID = ? AND DEVICE_NAME = ?
		</xsl:variable>

		<xsl:variable name="result">
			<dp:sql-execute source="'LOGDP'" statement="$statement">
				<arguments>
					<argument>
						<xsl:value-of select="$vars/audit/username/text()" />
					</argument>
					<argument>
						<xsl:value-of select="$vars/audit/scope/text()" />
					</argument>
					<argument>
						<xsl:value-of select="$vars/audit/uuid/text()" />
					</argument>
					<argument>
						<xsl:value-of select="$vars/audit/device-name/text()" />
					</argument>
				</arguments>
			</dp:sql-execute>
		</xsl:variable>

		<xsl:choose>
			<xsl:when test="$result/sql/@result = 'success'">
				<result>SUCCESS</result>
			</xsl:when>
			<xsl:when test="$result/sql/@result = 'error'">
				<result>FAILED</result>
			</xsl:when>
		</xsl:choose>

	</xsl:template>

</xsl:stylesheet>