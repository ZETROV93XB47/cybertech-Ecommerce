package com.novatech.cybertech.services.implementation.shopping;

import com.novatech.cybertech.dto.data.StripeCardDetailsDto;
import com.novatech.cybertech.dto.data.StripeSetupIntentDto;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.dto.response.user.BankCardSetupIntentResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.BankCardType;
import com.novatech.cybertech.events.StripePaymentMethodReleasedEvent;
import com.novatech.cybertech.exceptions.BankCardExpiredException;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.exceptions.InvalidCardSetupException;
import com.novatech.cybertech.exceptions.PaymentProviderNotConfiguredException;
import com.novatech.cybertech.exceptions.UnauthorizedBankCardAccessException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.builders.BankCardEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.mappers.entity.BankCardMapper;
import com.novatech.cybertech.repositories.BankCardRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.StripeCardGateway;
import com.novatech.cybertech.services.implementation.BankCardManagementServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link BankCardManagementServiceImp}: saved card at Stripe. The browser
 * confirms a SetupIntent in Stripe Elements; the service only ever sees Stripe ids, brand, last
 * four digits, expiry and holder name.
 */
@ExtendWith(MockitoExtension.class)
class BankCardManagementServiceImpTest {

    private static final String PUBLISHABLE_KEY = "pk_test_cybertech";
    private static final String CUSTOMER_ID = "cus_owner";
    private static final String SETUP_INTENT_ID = "seti_owner";
    private static final String NEW_PM = "pm_new_card";
    private static final int FUTURE_YEAR = YearMonth.now().getYear() + 3;

    @Mock BankCardMapper bankCardMapper;
    @Mock UserRepository userRepository;
    @Mock BankCardRepository bankCardRepository;
    @Mock StripeCardGateway stripeCardGateway;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks BankCardManagementServiceImp service;

    String keycloakId;
    UserEntity user;

    @BeforeEach
    void setUp() {
        keycloakId = "kc-" + UUID.randomUUID();
        user = UserEntityBuilder.aValidUserBuilder()
                .keycloakId(keycloakId)
                .firstName("Jane")
                .lastName("Doe")
                .stripeCustomerId(CUSTOMER_ID)
                .build();
        ReflectionTestUtils.setField(service, "publishableKey", PUBLISHABLE_KEY);
        lenient().when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
        lenient().when(bankCardRepository.save(any(BankCardEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(bankCardMapper.mapFromEntityToResponseDto(any(BankCardEntity.class)))
                .thenReturn(UserDtoFixtures.aSampleBankCardResponse());
    }

    private static StripeSetupIntentDto succeededSetup(final String customerId) {
        return new StripeSetupIntentDto(SETUP_INTENT_ID, "secret", customerId, "succeeded", NEW_PM);
    }

    private static StripeCardDetailsDto card(final String customerId, final String brand, final String holder) {
        return new StripeCardDetailsDto(NEW_PM, customerId, brand, "4242", 12, FUTURE_YEAR, holder);
    }

    private static BankCardCreationRequestDto addRequest() {
        return BankCardCreationRequestDto.builder().setupIntentId(SETUP_INTENT_ID).build();
    }

    @Nested
    @DisplayName("createSetupIntent")
    class CreateSetupIntent {

        @Test
        @DisplayName("first card: creates the Stripe Customer, stores its id, returns secret + publishable key")
        void createsCustomerOnFirstUse() {
            user.setStripeCustomerId(null);
            when(stripeCardGateway.createCustomer(user.getUuid(), user.getEmail(), "Jane Doe")).thenReturn("cus_created");
            when(stripeCardGateway.createSetupIntent("cus_created"))
                    .thenReturn(new StripeSetupIntentDto("seti_1", "seti_1_secret", "cus_created", "requires_payment_method", null));

            final BankCardSetupIntentResponseDto response = service.createSetupIntent(keycloakId);

            assertThat(response.setupIntentId()).isEqualTo("seti_1");
            assertThat(response.clientSecret()).isEqualTo("seti_1_secret");
            assertThat(response.publishableKey()).isEqualTo(PUBLISHABLE_KEY);
            assertThat(user.getStripeCustomerId()).isEqualTo("cus_created");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("existing Customer is reused, never recreated")
        void reusesExistingCustomer() {
            when(stripeCardGateway.createSetupIntent(CUSTOMER_ID))
                    .thenReturn(new StripeSetupIntentDto("seti_2", "s", CUSTOMER_ID, "requires_payment_method", null));

            service.createSetupIntent(keycloakId);

            verify(stripeCardGateway, never()).createCustomer(any(), any(), any());
        }

        @Test
        @DisplayName("no publishable key configured: 503 before any Stripe call")
        void missingPublishableKey() {
            ReflectionTestUtils.setField(service, "publishableKey", "");

            assertThatThrownBy(() -> service.createSetupIntent(keycloakId))
                    .isInstanceOf(PaymentProviderNotConfiguredException.class);
            verifyNoInteractions(stripeCardGateway);
        }
    }

    @Nested
    @DisplayName("addBankCard (save the card confirmed in Stripe Elements)")
    class AddBankCard {

        @Test
        @DisplayName("first card: stored from Stripe's PaymentMethod and made the Customer's default")
        void savesFirstCard() {
            when(stripeCardGateway.retrieveSetupIntent(SETUP_INTENT_ID)).thenReturn(succeededSetup(CUSTOMER_ID));
            when(stripeCardGateway.retrievePaymentMethod(NEW_PM)).thenReturn(card(CUSTOMER_ID, "mastercard", "J DOE"));

            service.addBankCard(keycloakId, addRequest());

            final ArgumentCaptor<BankCardEntity> saved = ArgumentCaptor.forClass(BankCardEntity.class);
            verify(bankCardRepository).save(saved.capture());
            final BankCardEntity entity = saved.getValue();
            assertThat(entity.getStripePaymentMethodId()).isEqualTo(NEW_PM);
            assertThat(entity.getCardType()).isEqualTo(BankCardType.MASTERCARD);
            assertThat(entity.getLastFourDigits()).isEqualTo("4242");
            assertThat(entity.getExpiryDate()).isEqualTo("12/" + FUTURE_YEAR);
            assertThat(entity.getCardHolderName()).isEqualTo("J DOE");
            assertThat(entity.getUserEntity()).isSameAs(user);
            verify(stripeCardGateway).setDefaultPaymentMethod(CUSTOMER_ID, NEW_PM);
            verify(stripeCardGateway, never()).attachPaymentMethod(anyString(), anyString());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("holder name not collected by Elements: falls back to the user's name")
        void holderNameFallsBackToUserName() {
            when(stripeCardGateway.retrieveSetupIntent(SETUP_INTENT_ID)).thenReturn(succeededSetup(CUSTOMER_ID));
            when(stripeCardGateway.retrievePaymentMethod(NEW_PM)).thenReturn(card(CUSTOMER_ID, "visa", null));

            service.addBankCard(keycloakId, addRequest());

            final ArgumentCaptor<BankCardEntity> saved = ArgumentCaptor.forClass(BankCardEntity.class);
            verify(bankCardRepository).save(saved.capture());
            assertThat(saved.getValue().getCardHolderName()).isEqualTo("Jane Doe");
        }

        @Test
        @DisplayName("PaymentMethod not attached yet: attached to the caller's Customer")
        void attachesUnattachedPaymentMethod() {
            when(stripeCardGateway.retrieveSetupIntent(SETUP_INTENT_ID)).thenReturn(succeededSetup(CUSTOMER_ID));
            when(stripeCardGateway.retrievePaymentMethod(NEW_PM)).thenReturn(card(null, "visa", "J"));

            service.addBankCard(keycloakId, addRequest());

            verify(stripeCardGateway).attachPaymentMethod(NEW_PM, CUSTOMER_ID);
        }

        @Test
        @DisplayName("a new card replaces the old one in place; the old PaymentMethod is released after commit")
        void replacesPreviousCard() {
            final BankCardEntity previous = BankCardEntityBuilder.aValidBankCardBuilder()
                    .stripePaymentMethodId("pm_old")
                    .userEntity(user)
                    .build();
            user.setBankCardEntity(previous);
            when(stripeCardGateway.retrieveSetupIntent(SETUP_INTENT_ID)).thenReturn(succeededSetup(CUSTOMER_ID));
            when(stripeCardGateway.retrievePaymentMethod(NEW_PM)).thenReturn(card(CUSTOMER_ID, "visa", "J"));

            service.addBankCard(keycloakId, addRequest());

            verify(bankCardRepository).save(previous);
            assertThat(previous.getStripePaymentMethodId()).isEqualTo(NEW_PM);
            final ArgumentCaptor<StripePaymentMethodReleasedEvent> released =
                    ArgumentCaptor.forClass(StripePaymentMethodReleasedEvent.class);
            verify(eventPublisher).publishEvent(released.capture());
            assertThat(released.getValue().getPaymentMethodId()).isEqualTo("pm_old");
        }

        @Test
        @DisplayName("legacy card (encrypted number, no PaymentMethod) is replaced, nothing to release at Stripe")
        void replacesLegacyCardWithoutRelease() {
            final BankCardEntity legacy = BankCardEntityBuilder.aValidBankCardBuilder().userEntity(user).build();
            legacy.setStripePaymentMethodId(null);
            user.setBankCardEntity(legacy);
            when(stripeCardGateway.retrieveSetupIntent(SETUP_INTENT_ID)).thenReturn(succeededSetup(CUSTOMER_ID));
            when(stripeCardGateway.retrievePaymentMethod(NEW_PM)).thenReturn(card(CUSTOMER_ID, "visa", "J"));

            service.addBankCard(keycloakId, addRequest());

            assertThat(legacy.getStripePaymentMethodId()).isEqualTo(NEW_PM);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("SetupIntent of another Customer: 403, nothing saved")
        void rejectsForeignSetupIntent() {
            when(stripeCardGateway.retrieveSetupIntent(SETUP_INTENT_ID)).thenReturn(succeededSetup("cus_someone_else"));

            assertThatThrownBy(() -> service.addBankCard(keycloakId, addRequest()))
                    .isInstanceOf(UnauthorizedBankCardAccessException.class);
            verify(bankCardRepository, never()).save(any());
            verify(stripeCardGateway, never()).retrievePaymentMethod(anyString());
        }

        @Test
        @DisplayName("PaymentMethod attached to another Customer: 403")
        void rejectsForeignPaymentMethod() {
            when(stripeCardGateway.retrieveSetupIntent(SETUP_INTENT_ID)).thenReturn(succeededSetup(CUSTOMER_ID));
            when(stripeCardGateway.retrievePaymentMethod(NEW_PM)).thenReturn(card("cus_someone_else", "visa", "J"));

            assertThatThrownBy(() -> service.addBankCard(keycloakId, addRequest()))
                    .isInstanceOf(UnauthorizedBankCardAccessException.class);
            verify(bankCardRepository, never()).save(any());
        }

        @Test
        @DisplayName("user without Stripe Customer never created a SetupIntent: 403")
        void rejectsUserWithoutCustomer() {
            user.setStripeCustomerId(null);

            assertThatThrownBy(() -> service.addBankCard(keycloakId, addRequest()))
                    .isInstanceOf(UnauthorizedBankCardAccessException.class);
            verifyNoInteractions(stripeCardGateway);
        }

        @Test
        @DisplayName("SetupIntent not succeeded (card not confirmed in Elements): 400")
        void rejectsUnfinishedSetup() {
            when(stripeCardGateway.retrieveSetupIntent(SETUP_INTENT_ID))
                    .thenReturn(new StripeSetupIntentDto(SETUP_INTENT_ID, "s", CUSTOMER_ID, "requires_payment_method", null));

            assertThatThrownBy(() -> service.addBankCard(keycloakId, addRequest()))
                    .isInstanceOf(InvalidCardSetupException.class);
            verify(bankCardRepository, never()).save(any());
        }

        @Test
        @DisplayName("expired card: BankCardExpiredException before any default/save")
        void rejectsExpiredCard() {
            when(stripeCardGateway.retrieveSetupIntent(SETUP_INTENT_ID)).thenReturn(succeededSetup(CUSTOMER_ID));
            final YearMonth lastMonth = YearMonth.now().minusMonths(1);
            when(stripeCardGateway.retrievePaymentMethod(NEW_PM)).thenReturn(new StripeCardDetailsDto(
                    NEW_PM, CUSTOMER_ID, "visa", "4242", lastMonth.getMonthValue(), lastMonth.getYear(), "J"));

            assertThatThrownBy(() -> service.addBankCard(keycloakId, addRequest()))
                    .isInstanceOf(BankCardExpiredException.class);
            verify(stripeCardGateway, never()).setDefaultPaymentMethod(anyString(), anyString());
        }

        @Test
        @DisplayName("user not found: UserNotFoundException")
        void userMissing() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addBankCard(keycloakId, addRequest()))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getCard")
    class GetCard {

        @Test
        @DisplayName("returns the masked DTO of the chargeable card")
        void returnsChargeableCard() {
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCard();
            when(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).thenReturn(List.of(card));

            assertThat(service.getCard(keycloakId)).isNotNull();
            verify(bankCardMapper).mapFromEntityToResponseDto(card);
        }

        @Test
        @DisplayName("legacy card without PaymentMethod is reported as absent (404)")
        void legacyCardIsAbsent() {
            final BankCardEntity legacy = BankCardEntityBuilder.aValidBankCard();
            legacy.setStripePaymentMethodId(null);
            when(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).thenReturn(List.of(legacy));

            assertThatThrownBy(() -> service.getCard(keycloakId)).isInstanceOf(BankCardNotFoundException.class);
        }

        @Test
        @DisplayName("no card: 404")
        void noCard() {
            when(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).thenReturn(List.of());

            assertThatThrownBy(() -> service.getCard(keycloakId)).isInstanceOf(BankCardNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateBankCard")
    class UpdateBankCard {

        @Test
        @DisplayName("updates holder name and expiry at Stripe, then locally")
        void updatesAtStripeThenLocally() {
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCardBuilder()
                    .stripePaymentMethodId("pm_current")
                    .userEntity(user)
                    .build();
            user.setBankCardEntity(card);
            final BankCardUpdateRequestDto dto = new BankCardUpdateRequestDto("New Holder", "06/" + FUTURE_YEAR);

            service.updateBankCard(keycloakId, dto);

            verify(stripeCardGateway).updatePaymentMethod("pm_current", "New Holder", 6, FUTURE_YEAR);
            assertThat(card.getCardHolderName()).isEqualTo("New Holder");
            assertThat(card.getExpiryDate()).isEqualTo("06/" + FUTURE_YEAR);
            verify(bankCardRepository).save(card);
        }

        @Test
        @DisplayName("Stripe refuses the change: nothing saved locally")
        void stripeRefusalKeepsLocalCard() {
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCardBuilder().userEntity(user).build();
            user.setBankCardEntity(card);
            final String holderBefore = card.getCardHolderName();
            org.mockito.Mockito.doThrow(new InvalidCardSetupException("refused"))
                    .when(stripeCardGateway).updatePaymentMethod(anyString(), anyString(), anyInt(), anyInt());

            assertThatThrownBy(() -> service.updateBankCard(keycloakId,
                    new BankCardUpdateRequestDto("Other", "06/" + FUTURE_YEAR)))
                    .isInstanceOf(InvalidCardSetupException.class);
            assertThat(card.getCardHolderName()).isEqualTo(holderBefore);
            verify(bankCardRepository, never()).save(any());
        }

        @Test
        @DisplayName("past expiry: BankCardExpiredException before Stripe")
        void pastExpiry() {
            user.setBankCardEntity(BankCardEntityBuilder.aValidBankCardBuilder().userEntity(user).build());

            assertThatThrownBy(() -> service.updateBankCard(keycloakId, new BankCardUpdateRequestDto("X", "01/2000")))
                    .isInstanceOf(BankCardExpiredException.class);
            verifyNoInteractions(stripeCardGateway);
        }

        @Test
        @DisplayName("no card (or legacy card): 404")
        void noCard() {
            assertThatThrownBy(() -> service.updateBankCard(keycloakId, UserDtoFixtures.aValidBankCardUpdateRequest()))
                    .isInstanceOf(BankCardNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteBankCard / deleteByUUID")
    class Delete {

        @Test
        @DisplayName("owner delete: row removed, PaymentMethod released at Stripe after commit")
        void ownerDeleteReleasesPaymentMethod() {
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCardBuilder()
                    .stripePaymentMethodId("pm_gone")
                    .userEntity(user)
                    .build();
            user.setBankCardEntity(card);

            service.deleteBankCard(keycloakId);

            verify(bankCardRepository).delete(card);
            final ArgumentCaptor<StripePaymentMethodReleasedEvent> released =
                    ArgumentCaptor.forClass(StripePaymentMethodReleasedEvent.class);
            verify(eventPublisher).publishEvent(released.capture());
            assertThat(released.getValue().getPaymentMethodId()).isEqualTo("pm_gone");
            assertThat(user.getBankCardEntity()).isNull();
        }

        @Test
        @DisplayName("owner delete without card: 404")
        void ownerDeleteWithoutCard() {
            assertThatThrownBy(() -> service.deleteBankCard(keycloakId)).isInstanceOf(BankCardNotFoundException.class);
        }

        @Test
        @DisplayName("admin delete: row removed and PaymentMethod released")
        void adminDeleteReleasesPaymentMethod() {
            final UUID uuid = UUID.randomUUID();
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCardBuilder()
                    .uuid(uuid)
                    .stripePaymentMethodId("pm_admin_gone")
                    .build();
            when(bankCardRepository.findByUuid(uuid)).thenReturn(Optional.of(card));

            service.deleteByUUID(uuid, "kc-admin");

            verify(bankCardRepository).deleteByUuid(uuid);
            verify(eventPublisher).publishEvent(any(StripePaymentMethodReleasedEvent.class));
        }

        @Test
        @DisplayName("admin delete of a missing card: no exception, nothing released")
        void adminDeleteMissing() {
            final UUID uuid = UUID.randomUUID();
            when(bankCardRepository.findByUuid(uuid)).thenReturn(Optional.empty());

            service.deleteByUUID(uuid, "kc-admin");

            verify(bankCardRepository, never()).deleteByUuid(any());
            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("admin reads")
    class AdminReads {

        @Test
        void getAllMapsThePage() {
            final Pageable pageable = PageRequest.of(0, 10);
            final Page<BankCardEntity> page = new PageImpl<>(List.of(BankCardEntityBuilder.aValidBankCard()));
            when(bankCardRepository.findAll(pageable)).thenReturn(page);

            final Page<BankCardResponseDto> result = service.getAll(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        void getByUuidMissing() {
            final UUID uuid = UUID.randomUUID();
            when(bankCardRepository.findByUuid(uuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByUUID(uuid)).isInstanceOf(BankCardNotFoundException.class);
        }
    }
}
