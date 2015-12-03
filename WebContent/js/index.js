function getAnalysis() {

	var inputTag = $('#inputTag').val();
	if (!inputTag || inputTag == "") {
		$('#info-txt').html("Insert a tag!");
		return false;
	} else {
		$('.results-container').hide();
		$('.info-container').hide();
		$('.info-container').css('color','black');
		$('#info-txt').html("Results will appear here");
		$('.tweets-list').hide();
		$('.tweets-button-show').show();
		$('.loading').show();
	}
	
	var inputContext = $('#inputContext').val();
	if (!inputContext)
		inputContext = inputTag;


	
	$.ajax({
		url: "index",
		type: 'GET',
//		contentType:'json',
		data: {
			tag: inputTag,
			context: inputContext
		},
  		success: function(data) {
  	    	$('.loading').hide();
  	    	$('.results-container').show();
  	
  	    	if (typeof data == "string")
  	    		data = $.parseXML(data);
  	    	data = JSON.parse(xml2json(data,""));
  	    	var score = data.results.docSentiment.score;
  	    	var sentiment = data.results.docSentiment.type;
  	    	
  	    	if (score)
  	    		$('.sent-percent').html(parseInt(score*100)+"%");
  	    	var color = score > 0 ? "green" : "red";
  	    	$('.sent-percent').css('color',color);

	    	if (sentiment == "positive") {
		  		$('#smiley').show();
		  		$('#sad').hide();
		  		readTweetsAndWrite("tweets/savedTweets.txt");
		  		$('.tweets-list').show();
		  	} else if (sentiment == "negative") {
		  		$('#sad').show();
		  		$('#smiley').hide();
		  		readTweetsAndWrite("tweets/savedTweets.txt");
		  		$('.tweets-list').show();
		  	} else {
		  		$('.results-container').hide();
		  		$('.info-container').show();
		  		$('#info-txt').html("The sentiment could not be retrieved!");
		  	}

	    	
  			
		},
		error: function(xhr, textStatus, thrownError) {
	    	$('.loading').hide();
	    	$('.info-container').show();
	    	$('.info-container').css('color','red');
	    	$('.info-container').html(textStatus+": "+thrownError);

	    	if (xhr.status == "502")
	    		$('.info-container').html("Not enough tweets found: try with different keywords");		
		}
	});
	
}

function showTweets() {
	$('.tweets-list').show();	
	readTweetsAndWrite("tweets/savedTweets.txt");
	$('.tweets-button-show').hide();
    scrollToDiv('tweet-ref');
}

function hideTweets() {
	$('.tweets-list').hide();	
	$('.tweets-button-show').show();
	scrollToDiv('top-div');
	
}

function getXmlString(xml) {
	  if (window.ActiveXObject) { return xml.xml; }
	  return new XMLSerializer().serializeToString(xml);
	}

function readTweetsAndWrite(file)
{
    var rawFile = new XMLHttpRequest();
    rawFile.open("GET", file, false);
    rawFile.onreadystatechange = function ()
    {
        if(rawFile.readyState === 4)
        {
            if(rawFile.status === 200 || rawFile.status == 0)
            {
                var allText = rawFile.responseText;
                $('#tweets-date').html(decodeURIComponent(escape(allText.substring(0,47))))
                $('#tw-list').html(decodeURIComponent(escape(allText.substring(47,allText.length).trim())));
                
            }
        }
    }
    rawFile.send(null);
}

function scrollToDiv(divId) {
    $('html, body').animate({ scrollTop: $('#'+divId).offset().top }, 'slow');
    return false;
}

