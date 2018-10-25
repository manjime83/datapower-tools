<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:dp="http://www.datapower.com/extensions" xmlns:date="http://exslt.org/dates-and-times" extension-element-prefixes="dp date" exclude-result-prefixes="dp date">

	<dp:input-mapping href="store:///pkcs7-convert-input.ffd" type="ffd" />

	<xsl:template match="/">

		<xsl:variable name="vars">
			<xsl:variable name="time-value" select="dp:time-value()" />
			<audit>
				<response-entity>
					<xsl:value-of select="string(dp:decode(dp:binary-encode(/object/message/node()), 'base-64'))" />
				</response-entity>
				<response-time>
					<xsl:value-of select="concat(date:add('1969-12-31T19:00:00', concat('PT', floor($time-value div 1000), 'S')), '.', $time-value mod 1000)" />
				</response-time>
				<uuid>
					<xsl:value-of select="dp:variable('var://context/api.gateway/uuid')" />
				</uuid>
				<device-name>
					<xsl:value-of select="string(dp:variable('var://service/system/ident')/identification/device-name)" />
				</device-name>
			</audit>
		</xsl:variable>

		<xsl:variable name="statement">
			UPDATE OAUTH_EP_AUDIT SET RESPONSE_TIME = ?, RESPONSE_ENTITY = ? WHERE UUID = ? AND DEVICE_NAME = ?
		</xsl:variable>

		<dp:dump-nodes file="concat('vars.oauth.ep.audit.response.', dp:variable('var://service/transaction-rule-type'), '.xml')" nodes="$vars" />

		<xsl:variable name="result">
			<dp:sql-execute source="'api.gateway'" statement="$statement">
				<arguments>
					<argument>
						<xsl:value-of select="$vars/audit/response-time/text()" />
					</argument>
					<argument>
						<xsl:value-of select="$vars/audit/response-entity/text()" />
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

		<dp:dump-nodes file="concat('oauth.ep.audit.response.', dp:variable('var://service/transaction-rule-type'), '.xml')" nodes="$result" />

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