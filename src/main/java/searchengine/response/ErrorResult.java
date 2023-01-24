package searchengine.response;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Setter
@Getter
public class ErrorResult extends OkResult implements ResponseResult {
    private String error;

    public static ResponseEntity<ResponseResult> get(String error, int code){
        ErrorResult err = new ErrorResult();
        err.setResult(false);
        err.setError(error);
        return switch (code) {
            case 400 -> new ResponseEntity<>(err, HttpStatus.BAD_REQUEST);
            case 404 -> new ResponseEntity<>(err, HttpStatus.NOT_FOUND);
            case 405 -> new ResponseEntity<>(err, HttpStatus.METHOD_NOT_ALLOWED);
            case 503 -> new ResponseEntity<>(err, HttpStatus.SERVICE_UNAVAILABLE);
            default -> ResponseEntity.ok(err);
        };
    }
}
