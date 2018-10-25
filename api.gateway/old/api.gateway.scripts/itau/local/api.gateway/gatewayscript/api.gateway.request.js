var transform = require("transform");
var sm = require("service-metadata");
var hm = require('header-metadata');

transform.xpath("/aaa-error-logs/aaa-error-log[@direction = 'request']/error[@target = 'oauth-client']", XML
		.parse(sm.aaaErrorLogs), function(error, nodeList) {
	if (error) {
		sm.mpgw.skipBackside = true;
		session.output.write({
			message : error.errorMessage,
			code : error.errorCode,
			description : error.errorDescription,
			suggestion : error.errorSuggestion
		});
	} else {
		if (nodeList.length > 0) {
			var id = nodeList.item(0).getElementsByTagName("id").item(0).textContent;
			var description = nodeList.item(0).getElementsByTagName("description").item(0).textContent;

			sm.mpgw.skipBackside = true;
			session.output.write({
				error : id,
				error_description : description
			});
			sm.errorIgnore = true;
		} else {
			session.input.readAsBuffer(function(error, buffer) {
				if (error) {
					sm.mpgw.skipBackside = true;
					session.output.write({
						message : error.errorMessage,
						code : error.errorCode,
						description : error.errorDescription,
						suggestion : error.errorSuggestion
					});
				} else {
					sm.mpgw.proxyContentType = true;
					hm.current.remove("Authorization");
				}
			});
		}
	}
});