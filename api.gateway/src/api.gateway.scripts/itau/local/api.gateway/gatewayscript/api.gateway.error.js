var transform = require("transform");
var sm = require("service-metadata");
var hm = require('header-metadata');

transform.xpath("/aaa-error-logs/aaa-error-log[@direction = 'request']/error[@target = 'oauth-client']", XML
		.parse(sm.aaaErrorLogs), function(error, nodeList) {
	hm.current.set("Content-Type", "application/json");
	if (error) {
		session.output.write({
			message: error.errorMessage,
			code: error.errorCode,
			description: error.errorDescription,
			suggestion: error.errorSuggestion
		});
	} else {
		if (nodeList.length > 0) {
			var id = nodeList.item(0).getElementsByTagName("id").item(0).textContent;
			var description = nodeList.item(0).getElementsByTagName("description").item(0).textContent;

			session.output.write({
				error : id,
				error_description : description
			});
		} else {
			session.output.write({
				error : sm.errorCode,
				error_description : sm.errorMessage
			});
		}
	}
});