package sd2223.trab1.server.REST;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import sd2223.trab1.api.java.Result;

public class RestResource {

    /**
     * Given a Result<T>, either returns the value, or throws the JAX-WS Exception
     * matching the error code...
     */
    protected <T> T fromJavaResult(Result<T> result) {
        if (result.isOK())
            return result.value();
        else
            throw new WebApplicationException(statusCodeFrom(result));
    }

    /**
     * Translates a Result<T> to a HTTP Status code
     */
    private static Response.Status statusCodeFrom(Result<?> result) {
        switch (result.error()) {
            case CONFLICT:
                return Response.Status.CONFLICT;
            case NOT_FOUND:
                return Response.Status.NOT_FOUND;
            case FORBIDDEN:
                return Response.Status.FORBIDDEN;
            case TIMEOUT:
            case BAD_REQUEST:
                return Response.Status.BAD_REQUEST;
            case NOT_IMPLEMENTED:
                return Response.Status.NOT_IMPLEMENTED;
            case INTERNAL_ERROR:
                return Response.Status.INTERNAL_SERVER_ERROR;
            case OK:
                return result.value() == null ? Response.Status.NO_CONTENT : Response.Status.OK;
            default:
                return Response.Status.INTERNAL_SERVER_ERROR;
        }
    }
}
