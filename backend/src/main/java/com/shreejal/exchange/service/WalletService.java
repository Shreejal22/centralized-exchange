package com.shreejal.exchange.service;

import com.shreejal.exchange.model.Asset;
import com.shreejal.exchange.model.User;
import com.shreejal.exchange.model.Wallet;
import com.shreejal.exchange.repository.AssetRepository;
import com.shreejal.exchange.repository.UserRepository;
import com.shreejal.exchange.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WalletService {
    private final WalletRepository walletRepository;
    private final AssetRepository assetRepository;
    private final BalanceService balanceService;
    private final UserRepository userRepository;

    public WalletService(WalletRepository walletRepository, AssetRepository assetRepository, BalanceService balanceService, UserRepository userRepository) {
        this.walletRepository = walletRepository;
        this.assetRepository = assetRepository;
        this.balanceService = balanceService;
        this.userRepository = userRepository;
    }

    @Transactional
    public Wallet getOrCreateWallet(Long userId){
        if(userId==null||userId<=0){
            throw new IllegalArgumentException("User Id must be positive");
        }
        return walletRepository.findByUser_Id(userId)
                .orElseGet(()->{
                    User user=userRepository.findById(userId)
                            .orElseThrow(()-> new EntityNotFoundException("User not found"+userId));

                Wallet wallet = new Wallet() ;
                wallet.setUser(user);
                Wallet savedWallet=walletRepository.save(wallet);
                List<Asset> assets=assetRepository.findAll();
                for( Asset asset:assets){
                    balanceService.initialise(savedWallet,asset);
                }
                return savedWallet;
                        });
    }
}
