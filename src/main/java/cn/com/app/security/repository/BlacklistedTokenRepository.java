package cn.com.app.security.repository;

import cn.com.app.security.domain.BlacklistedToken;
import tk.mybatis.mapper.common.Mapper;

public interface BlacklistedTokenRepository extends Mapper<BlacklistedToken> {

    default boolean existsByTokenId(String tokenId) {
        BlacklistedToken probe = new BlacklistedToken();
        probe.setTokenId(tokenId);
        return selectCount(probe) > 0;
    }

    @Override
    default int save(BlacklistedToken token) {
        if (token.getId() == null) {
            return insertSelective(token);
        }
        return updateByPrimaryKeySelective(token);
    }
}
