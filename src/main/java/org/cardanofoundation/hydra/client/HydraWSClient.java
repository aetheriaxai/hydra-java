package org.cardanofoundation.hydra.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.cardanofoundation.hydra.client.model.HydraState;
import org.cardanofoundation.hydra.client.model.UTXO;
import org.cardanofoundation.hydra.client.model.query.request.*;
import org.cardanofoundation.hydra.client.model.query.request.base.Tag;
import org.cardanofoundation.hydra.client.model.query.response.*;
import org.cardanofoundation.hydra.client.model.query.response.base.QueryResponse;
import org.cardanofoundation.hydra.client.util.MoreJson;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
// not thread safe yet
public class HydraWSClient extends WebSocketClient {

    private Optional<HydraStateEventListener> hydraStateEventListener = Optional.empty();

    private Optional<HydraQueryEventListener> hydraQueryEventListener = Optional.empty();

    private Map<Tag, Function<JsonNode, QueryResponse>> handlers = new HashMap<>();

    private HydraState hydraState = HydraState.Unknown;

    public HydraWSClient(URI serverURI) {
        super(serverURI);
        initStateMachine();
        createResponseHandlers();
    }

    private void initStateMachine() {
        this.hydraState = HydraState.Unknown;
    }

    private void createResponseHandlers() {
        handlers.put(Tag.Greetings, GreetingsResponse::create);
        handlers.put(Tag.PeerConnected, PeerConnectedResponse::create);
        handlers.put(Tag.PeerDisconnected, PeerDisconnectedResponse::create);
        handlers.put(Tag.ReadyToCommit, ReadyToCommitResponse::create);
        handlers.put(Tag.Committed, CommittedResponse::create);
        handlers.put(Tag.HeadIsOpen, HeadIsOpenResponse::create);
        handlers.put(Tag.HeadIsClosed, HeadIsClosedResponse::create);
        handlers.put(Tag.HeadIsContested, HeadIsContestedResponse::create);
        handlers.put(Tag.ReadyToFanout, ReadyToFanoutResponse::create);
        handlers.put(Tag.HeadIsAborted, HeadIsAbortedResponse::create);
        handlers.put(Tag.HeadIsFinalized, HeadIsFinalizedResponse::create);
        handlers.put(Tag.TxSeen, TxSeenResponse::create);
        handlers.put(Tag.TxValid, TxValidResponse::create);
        handlers.put(Tag.TxInvalid, TxInvalidResponse::create);
        handlers.put(Tag.TxExpired, TxExpiredResponse::create);
        handlers.put(Tag.GetUTxOResponse, GetUTxOResponse::create);
        handlers.put(Tag.InvalidInput, InvalidInputResponse::create);
        handlers.put(Tag.PostTxOnChainFailed, PostChainTxFailedResponse::create);
        handlers.put(Tag.RolledBack, RolledbackResponse::create);
        handlers.put(Tag.CommandFailed, CommandFailedResponse::create);
    }

    public HydraState getHydraState() {
        return hydraState;
    }

    public void setHydraQueryEventListener(HydraQueryEventListener hydraQueryEventListener) {
        this.hydraQueryEventListener = Optional.ofNullable(hydraQueryEventListener);
    }

    public void setHydraStateEventListener(HydraStateEventListener hydraStateEventListener) {
        this.hydraStateEventListener = Optional.ofNullable(hydraStateEventListener);
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        log.info("Connection Established!");
        log.debug("onOpen -> ServerHandshake: {}", serverHandshake);
        hydraState = HydraState.Idle;
    }

    @Override
    public void onMessage(String message) {
        log.debug("Received: {}", message);

        val raw = MoreJson.read(message);
        val tagString = raw.get("tag").asText();
        val maybeTag = Tag.find(tagString);

        if (maybeTag.isEmpty()) {
            log.warn("We don'tag support maybeTag:{} yet, json:{}", tagString, message);
            return;
        }

        val tag = maybeTag.orElseThrow();
        val queryResponse = handlers.get(tag).apply(raw);

        hydraQueryEventListener.ifPresent(s -> s.onQueryResponse(queryResponse));

        queryResponse.getTag().getState().ifPresent(lState -> {
            val prev = hydraState;
            hydraState = lState;
            hydraStateEventListener.ifPresent(l -> l.onStateChanged(prev, hydraState));
        });
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("Connection closed by {}, Code: {}{}", (remote ? "remote peer" : "client"), code,
                (reason == null || reason.isEmpty()) ? reason : ", Reason: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        log.error("websocket error: {}", ex.getMessage());
    }

    // Initializes a new Head. This command is a no-op when a Head is already open and the server will output an CommandFailed message should this happen.
    public boolean init(int period) {
        if (hydraState == HydraState.Idle) {
            val request = new InitRequest(period);
            send(request.getRequestBody());
            return true;
        }

        return false;
    }

    // Aborts a head before it is opened. This can only be done before all participants have committed. Once opened, the head can't be aborted anymore but it can be closed using: Close.
    public boolean abort() {
        if (hydraState == HydraState.Initializing) {
            val request = new AbortHeadRequest();
            send(request.getRequestBody());

            return true;
        }

        return false;
    }

    // Join an initialized head. This is how parties get to inject funds inside a head. Note however that the utxo is an object and can be empty should a participant wants to join a head without locking any funds.
    public boolean commit(String utxoId, UTXO utxo) {
        if (hydraState == HydraState.Initializing) {
            val request = new CommitRequest();
            request.addUTXO(utxoId, utxo);
            send(request.getRequestBody());

            return true;
        }

        return false;
    }

    // Join an initialized head. This is how parties get to inject funds inside a head. Note however that the utxo is an object and can be empty should a participant wants to join a head without locking any funds.
    public boolean commit() {
        if (hydraState == HydraState.Initializing) {
            val request = new CommitRequest();
            send(request.getRequestBody());

            return true;
        }

        return false;
    }

    // Submit a transaction through the head. Note that the transaction is only broadcast if well-formed and valid.
    public boolean newTx(String transaction) {
        if (hydraState == HydraState.Open) {
            val request = new NewTxRequest(transaction);
            send(request.getRequestBody());

            return true;
        }

        return false;
    }

    // Terminate a head with the latest known snapshot. This effectively moves the head from the Open state to the Close state where the contestation phase begin. As a result of closing a head, no more transactions can be submitted via NewTx.
    public boolean closeHead() {
        if (hydraState == HydraState.Open) {
            val request = new CloseHeadRequest();
            send(request.getRequestBody());

            return true;
        }

        return false;
    }

    // Challenge the latest snapshot announced as a result of a head closure from another participant. Note that this necessarily contest with the latest snapshot known of your local Hydra node. Participants can only contest once.
    public boolean contest() {
        if (hydraState == HydraState.Closed) {
            val request = new ContestHeadRequest();
            send(request.getRequestBody());

            return true;
        }

        return false;
    }


    // Finalize a head after the contestation period passed. This will distribute the final (as closed and maybe contested) head state back on the layer 1.
    public boolean fanOut() {
        if (hydraState == HydraState.FanoutPossible) {
            val request = new FanoutRequest();
            send(request.getRequestBody());

            return true;
        }

        return false;
    }

    // Asynchronously access the current UTxO set of the Hydra node. This eventually triggers a UTxO event from the server.
    public boolean getUTXO() {
        if (hydraState == HydraState.Open) {
            val request = new GetUTxORequest();
            send(request.getRequestBody());

            return true;
        }

        return false;
    }

}
