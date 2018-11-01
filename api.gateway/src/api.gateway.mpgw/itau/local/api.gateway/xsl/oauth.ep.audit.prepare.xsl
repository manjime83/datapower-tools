<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:dp="http://www.datapower.com/extensions" xmlns:date="http://exslt.org/dates-and-times" extension-element-prefixes="dp date" exclude-result-prefixes="dp date">

	<dp:input-mapping href="store:///pkcs7-convert-input.ffd" type="ffd" />

	<xsl:output method="xml" />

	<xsl:template match="/">

		<xsl:variable name="vars">
			<xsl:variable name="time-value" select="dp:time-value()" />
			<dp:set-variable name="'var://context/api.gateway/uuid'" value="dp:generate-uuid()" />
			<audit>
				<uuid>
					<xsl:value-of select="dp:variable('var://context/api.gateway/uuid')" />
				</uuid>
				<device-name>
					<xsl:value-of select="string(dp:variable('var://service/system/ident')/identification/device-name)" />
				</device-name>
				<transaction-id>
					<xsl:value-of select="dp:variable('var://service/global-transaction-id')" />
				</transaction-id>
				<client-ip>
					<xsl:value-of select="dp:variable('var://service/transaction-client')" />
				</client-ip>
				<uri>
					<xsl:value-of select="concat(translate(dp:variable('var://service/protocol-method'), ' ', ''), ' ', dp:variable('var://service/URI'))" />
				</uri>
				<request-entity>
					<xsl:value-of select="string(dp:decode(dp:binary-encode(/object/message/node()), 'base-64'))" />
				</request-entity>
				<request-time>
					<xsl:value-of select="concat(date:add('1969-12-31T19:00:00', concat('PT', floor($time-value div 1000), 'S')), '.', $time-value mod 1000)" />
				</request-time>
			</audit>
		</xsl:variable>

		<xsl:variable name="statement">
			INSERT INTO OAUTH_EP_AUDIT (UUID, DEVICE_NAME, TRANSACTION_ID, CLIENT_IP, URI, REQUEST_ENTITY, REQUEST_TIME) VALUES (?, ?, ?, ?, ?, ?, ?)
		</xsl:variable>

		<xsl:variable name="result">
			<dp:sql-execute source="'LOGDP'" statement="$statement">
				<arguments>
					<argument>
						<xsl:value-of select="$vars/audit/uuid/text()" />
					</argument>
					<argument>
						<xsl:value-of select="$vars/audit/device-name/text()" />
					</argument>
					<argument>
						<xsl:value-of select="$vars/audit/transaction-id/text()" />
					</argument>
					<argument>
						<xsl:value-of select="$vars/audit/client-ip/text()" />
					</argument>
					<argument>
						<xsl:value-of select="$vars/audit/uri/text()" />
					</argument>
					<argument>
						<xsl:value-of select="$vars/audit/request-entity/text()" />
					</argument>
					<argument>
						<xsl:value-of select="$vars/audit/request-time/text()" />
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