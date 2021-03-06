window.game = (function () {

    var websocket;

    var playerUp = false;
    var gameStarted = false;
    var initialized = false;

    function startGame(){
        if(playerUp === true){
            websocket.send('start');
        }else{
            $("#playerAddress")
              .html("Waiting for player connection.");
        }

        gameStarted = true;
    }

    function onMessage(evt) {
        console.log(evt.data);

        var uiEvent = JSON.parse(evt.data);

        if(uiEvent.type === 'PlaneStatus'){
            onPlaneStatus(uiEvent);
        }
        else if (uiEvent.type === 'Score'){
            onScore(uiEvent);
        }
        else if (uiEvent.type === 'GameOver'){
            onGameOver(uiEvent);
        }
        else if (uiEvent.type === 'GameEnd'){
            onGameEnd(uiEvent);
        }
        else if (uiEvent.type === 'PlayerUp'){
            onPlayerUp(uiEvent);
        }
        else if (uiEvent.type === 'PlayerDown'){
            onPlayerDown(uiEvent);
        }
    }

    function onGameOver(uiEvent){
        websocket.close();
        $("#GameOver").modal();
    }

    function onGameEnd(uiEvent){
        websocket.close();
        $("#GameEnded").modal();
    }

    function onPlayerUp(uiEvent){
        $("#playerAddress")
            .html(uiEvent.address);

        playerUp = true;

        if(gameStarted === true){
            startGame();
        }
    }

    function onPlayerDown(uiEvent){
        $("#playerAddress")
            .html('Player is down');

        playerUp = false;
    }

    function onScore(newScore){
        $("#score")
            .find("#counter")
            .html(newScore.current + " / " + newScore.objective);

        $("#score")
            .find(".bar")
            .css('width',''+ (100 * newScore.current / newScore.objective) + '%');


        if(initialized == false){
            var intro = introJs();
            intro.onexit(startGame);
            intro.oncomplete(startGame);
            intro.start();

            initialized = true;
        }

    }

    function onPlaneStatus(uiEvent){
        var strip;

        strip = detachOrCreate(uiEvent.flightName);

        findStep(uiEvent.flightName,uiEvent.step)
            .append(strip);


        if(strip == undefined){
            strip = find(uiEvent.flightName);
        }

        if(uiEvent.error != ''){
            strip
                .addClass('error')
                .removeClass('regular')
                .find(".detail")
                .html(uiEvent.error);

        }else{
            strip.find(".detail")
                .html(uiEvent.detail);
        }
    }

    function create(flightName){
        var tableLine = $('<tr>',{
                html: '<td class="incoming"></td><td class="runway"></td><td class="taxiway"></td><td class="gate"></td><td class="done"></td>',
                id: flightName
        });

        $('#strips').prepend(tableLine);

       return  $('<div>',{
            html: '</i><p class="id"><i class="icon-plane"></i> '+flightName+'</p><p class="detail"></p>',
            class: 'strip strip-'+flightName
        }).addClass('regular');
    }

    function findStep(flightName,step){
        return $('#'+flightName).find('.'+step);
    }

    function find(flightName){
        return $('#' + flightName).find('.strip-' + flightName);
    }

    function detach(flightName){
        var strip = find(flightName);

        if(strip[0])
            return strip.detach();
    }

    function detachOrCreate(flightName){
        var detached = detach(flightName);

        if(detached)
            return detached;
        else
            return create(flightName);
    }

    function addFlight(flightName){
        $incoming.append(create(flightName));
    }

    return {
        init: function(uri){
            websocket = new WebSocket(uri);
            websocket.onmessage = function(evt) { onMessage(evt) };

            $('.active')
                .find('a[data-toggle=popover]')
                .popover({trigger: 'hover',placement: 'top'})
                .click(function(e) {
                    e.preventDefault()
                }
            );
        }
    };
})();