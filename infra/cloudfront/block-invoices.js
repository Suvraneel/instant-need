// Associate with the viewer-request event on every cache behavior that can
// reach this bucket. The admin API reads invoices directly from S3 via IAM.
function handler(event) {
    var uri = event.request.uri;
    try {
        for (var i = 0; i < 5; i++) {
            var decoded = decodeURIComponent(uri);
            if (decoded === uri) break;
            uri = decoded;
        }
    } catch (error) {
        return { statusCode: 403, statusDescription: 'Forbidden' };
    }

    var parts = uri.split('/');
    var path = [];
    for (var j = 0; j < parts.length; j++) {
        if (!parts[j] || parts[j] === '.') continue;
        if (parts[j] === '..') path.pop();
        else path.push(parts[j]);
    }
    if (path.length && path[0].toLowerCase() === 'invoices') {
        return { statusCode: 403, statusDescription: 'Forbidden' };
    }
    return event.request;
}
