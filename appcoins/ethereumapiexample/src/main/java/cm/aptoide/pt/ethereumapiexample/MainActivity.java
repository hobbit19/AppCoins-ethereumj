package cm.aptoide.pt.ethereumapiexample;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import cm.aptoide.pt.ethereum.EthereumApi;
import cm.aptoide.pt.ethereum.EthereumApiFactory;
import cm.aptoide.pt.ethereum.erc20.Erc20Transfer;
import cm.aptoide.pt.ethereum.ws.etherscan.BalanceResponse;
import cm.aptoide.pt.ethereum.ws.etherscan.TransactionResultResponse;
import cm.aptoide.pt.ethereumapiexample.NewAccountFragment.OnDeleteAccountConfirmedListener;
import cm.aptoide.pt.ethereumapiexample.PaySomethingFragment.OnPaymentConfirmedListener;
import cm.aptoide.pt.ethereumapiexample.R.id;
import cm.aptoide.pt.ethereumapiexample.R.layout;
import cm.aptoide.pt.web3j.abi.datatypes.Address;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements OnPaymentConfirmedListener,
    OnDeleteAccountConfirmedListener
{

  private static final long gasPrice = 24_000_000_000L;
  private static final long gasLimit = 0xfffff;

  private static final String CONTRACT_ADDRESS = "8dbf4349cbeca08a02cc6b5b0862f9dd42c585b9";
  private static final String RECEIVER_ADDR = "62a5c1680554A61334F5c6f6D7dA6044b6AFbFe8";
  private static final String MAIN_PREFS = "MainPrefs";

  private EthereumApi ethereumApi;
  private EtherAccountManager etherAccountManager;

  private TextView balanceTextView;
  private TextView yourAddress;
  private TextView addressTextView;
  private TextView amountTextView;

  private ScheduledExecutorService scheduledExecutorService;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(layout.activity_main);

    scheduledExecutorService = Executors.newScheduledThreadPool(1);

    assignViews();

    scheduledExecutorService.schedule(new Runnable() {
      @Override public void run() {
        ethereumApi = EthereumApiFactory.createEthereumApi();

        etherAccountManager =
            new EtherAccountManager(ethereumApi, getSharedPreferences(MAIN_PREFS, MODE_PRIVATE));

        runOnUiThread(new Runnable() {
          @Override public void run() {
            setMyAddress();
          }
        });
      }
    }, 0, TimeUnit.SECONDS);

    scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
      @Override public void run() {
        ethereumApi.getTokenBalance(CONTRACT_ADDRESS, Hex.toHexString(etherAccountManager.getECKey()
            .getAddress()))
            .subscribeOn(Schedulers.io())
            .subscribe(new Action1<BalanceResponse>() {
              @Override public void call(BalanceResponse balanceResponse) {
                runOnUiThread(new Runnable() {
                  @Override public void run() {
                    Toast.makeText(MainActivity.this, "Refreshing data", Toast.LENGTH_SHORT)
                        .show();
                  }
                });

                BigDecimal resultBigDecimal = new BigDecimal(balanceResponse.result);

                setBalance(balanceTextView,
                    resultBigDecimal.divide(new BigDecimal("100"), MathContext.DECIMAL32)
                        .toString());
              }
            });
      }
    }, 0, 5, TimeUnit.SECONDS);
  }

  private void setMyAddress() {
    yourAddress.setText(Hex.toHexString(etherAccountManager.getECKey()
        .getAddress()));
  }

  private void setBalance(TextView balanceTextView, String result) {
    runOnUiThread(new Runnable() {
      @Override public void run() {
        balanceTextView.setText(result);
      }
    });
  }

  private void assignViews() {
    balanceTextView = findViewById(id.balanceTextView);
    yourAddress = findViewById(id.your_address);
    addressTextView = findViewById(id.address_text_view);
    amountTextView = findViewById(id.amount);
  }

  public void paySomething(View v) {
    new PaySomethingFragment().show(getSupportFragmentManager(), "MyDialog");
  }

  @Override
  public void onPaymentConfirmed() {
    new Thread(new Runnable() {
      @Override public void run() {
        etherAccountManager.getCurrentNonce()
                .flatMap(new Func1<Long, Observable<TransactionResultResponse>>() {
                  @Override public Observable<TransactionResultResponse> call(Long nonce) {
                    return ethereumApi.call(nonce.intValue(), etherAccountManager.getECKey(),
                        gasPrice, gasLimit, new Address(CONTRACT_ADDRESS),
                        new Erc20Transfer(RECEIVER_ADDR, 1).encode());
                  }
                })
                //ethereumApi.call(nonce, CONTRACT_ADDRESS, erc20Transfer, etherAccountManager.getECKey())
                .subscribeOn(Schedulers.io())
                .subscribe(new Action1<Object>() {
                  @Override public void call(Object o) {
                    System.out.println(o);
                  }
                }, new Action1<Throwable>() {
                  @Override public void call(Throwable throwable) {
                    runOnUiThread(new Runnable() {
                      @Override public void run() {
                        Toast.makeText(MainActivity.this, throwable.getMessage(),
                            Toast.LENGTH_SHORT)
                            .show();
                      }
                    });
                  }
                });
      }
    }).start();
  }

  public void sendTokens(View view) {
    if (validateInputs((TextView) view, amountTextView)) {
      float floatAmount = Float.parseFloat(amountTextView.getText()
          .toString());

      int amount = (int) (floatAmount * 100);

      Erc20Transfer erc20 = new Erc20Transfer(addressTextView.getText()
          .toString()
          .substring(2), amount);

      etherAccountManager.getCurrentNonce()
          .doOnSubscribe(new Action0() {
            @Override public void call() {
              runOnUiThread(new Runnable() {
                @Override public void run() {
                  Toast.makeText(MainActivity.this, "Sending transaction", Toast.LENGTH_SHORT)
                      .show();
                  amountTextView.setText("");
                }
              });
            }
          })
          .flatMap(new Func1<Long, Observable<TransactionResultResponse>>() {
            @Override public Observable<TransactionResultResponse> call(Long nonce) {
              return ethereumApi.call(nonce.intValue(), etherAccountManager.getECKey(), gasPrice,
                  gasLimit, new Address(CONTRACT_ADDRESS), erc20.encode());
            }
          })
          .subscribeOn(Schedulers.io())
          .subscribe(new Action1<Object>() {
            @Override public void call(Object o) {
            }
          }, new Action1<Throwable>() {
            @Override public void call(Throwable throwable) {
              throwable.printStackTrace();
            }
          });
    } else {
      runOnUiThread(new Runnable() {
        @Override public void run() {
          Toast.makeText(MainActivity.this, "Address and Amount required!", Toast.LENGTH_SHORT)
              .show();
        }
      });
    }
  }

  private boolean validateInputs(TextView addressTextView, TextView amountTextView) {
    boolean validInputs = !StringUtils.isEmpty(addressTextView.getText()) && !StringUtils.isEmpty(
        amountTextView.getText());

    String s = amountTextView.getText()
        .toString();
    float value = !"".equals(s) ? Float.parseFloat(s) : 0;

    boolean limit = value > 100;

    return validInputs && !limit;
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
  }

  public void createWallet(View view) {
    new NewAccountFragment().show(getSupportFragmentManager(), "NewAccount");
  }

  @Override
  public void onDeleteAccountConfirmed() {
    etherAccountManager.createNewAccount();
    setMyAddress();
    balanceTextView.setText(Integer.toString(0));
  }
}
