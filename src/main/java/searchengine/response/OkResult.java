package searchengine.response;

import lombok.Data;
import org.springframework.http.ResponseEntity;

@Data
public class OkResult implements ResponseResult {
    private boolean result;
    public static ResponseEntity<ResponseResult> get(){
        OkResult ok = new OkResult();
        ok.setResult(true);
        return ResponseEntity.ok(ok);
    }

}
