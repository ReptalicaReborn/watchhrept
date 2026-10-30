/** An error whose message is safe to show the client, with the HTTP status to use. */
export class HttpError extends Error {
    constructor(
        readonly status: number,
        message: string,
    ) {
        super(message);
    }
}
