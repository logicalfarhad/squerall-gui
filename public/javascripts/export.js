$(document).ready(function(){
    let mp={};
    $("#generateMappings").click(function() {
        $.ajax({
            method: "GET",
            url: "/generateMappings",
            success: function(data) {
                mp = data
                $("#mappings-box").show()
                $("#exportMappings").show()
                $("#mappings-box").text(data.rml_text)
                $("#exportMappings").html("<br/><button type='button' class='btn btn-primary' id='saveMappings' style='margin-bottom: 10px;'>Export mappings</button>")
            },
            error: function(jqXHR, textStatus, errorThrown) {
                alert('An error occurred... open console for more information!');
                $('#result').html('<p>status code: '+jqXHR.status+'</p><p>errorThrown: ' + errorThrown + '</p><p>jqXHR.responseText:</p><div>'+jqXHR.responseText + '</div>');
                console.log('jqXHR:');
                console.log(jqXHR);
                console.log('textStatus:');
                console.log(textStatus);
                console.log('errorThrown:');
                console.log(errorThrown);
            }
        })
    })
    $("#mappings").on("click", "button#saveMappings", function () {
        $.post("http://localhost:3000/generatemapping",
            {data: mp}, function (data) {
                console.log(data)
                if (data === true) {
                    alert("RDF generated successfully")
                }
            })
    });
});
